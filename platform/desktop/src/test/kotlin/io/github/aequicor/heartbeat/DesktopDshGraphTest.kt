package io.github.aequicor.heartbeat

import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.di.DesktopGraph
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.sql.DriverManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DesktopDshGraphTest {

    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun registersDshInDesktopEngineCatalog() = withGraph { graph ->
        val facade = graph.aiEngineFacade
        val key = facade.availableEngines.value.single { it.id == "dsh" }
        assertEquals(AiEngineConnection.ACP, key.connection)
        assertEquals(AiEngineState.NotInitialized, facade.getEngine(key).state.value)
    }

    @Test
    fun connectsToInstalledDshAndStopsItsProcessTree() {
        Assume.assumeTrue(
            "Set HEARTBEAT_TEST_DSH=1 and an isolated DSH_HOME",
            System.getenv("HEARTBEAT_TEST_DSH") == "1",
        )
        val workspace = Path.of(checkNotNull(System.getenv("DSH_HOME"))).resolve("workspace")
        Files.createDirectories(workspace)

        withGraph { graph ->
            val facade = graph.aiEngineFacade
            val engine = facade.getEngine(facade.availableEngines.value.single { it.id == "dsh" })
            withTimeout(30.seconds) { verifyEngineConnection(engine, workspace) }
        }
    }

    private suspend fun verifyEngineConnection(engine: AiEngine, workspace: Path) {
        val existingProcesses = descendants().toSet()
        engine.dispatch(AiEngineEvent.Connect)
        val connection = engine.state.first { it != AiEngineState.NotInitialized && it != AiEngineState.Connecting }
        assertEquals(AiEngineState.Ready, connection)
        verifySessionResume(engine, workspace)

        val processes = descendants() - existingProcesses
        val minimumCount = if (System.getProperty("os.name").startsWith("Windows")) 2 else 1
        assertTrue(processes.size >= minimumCount)
        engine.dispatch(AiEngineEvent.Disconnect)
        engine.state.first { it == AiEngineState.NotInitialized }
        processes.forEach { it.onExit().await() }
    }

    private suspend fun verifySessionResume(engine: AiEngine, workspace: Path) {
        val config = SessionConfig(workspace.toString())
        val created = engine.getOrCreateSession(config).key
        engine.closeSession(created)
        val project = SessionListConfig.ByProject("test-project", workingDirectory = workspace.toString())
        val listed = engine.getListSessions(project).sessions.single()
        assertEquals(created, listed)
        val otherWorkspace = Files.createDirectories(workspace.resolveSibling("other-workspace"))
        val otherProject = project.copy(workingDirectory = otherWorkspace.toString())
        assertTrue(engine.getListSessions(otherProject).sessions.isEmpty())

        val session = engine.getOrCreateSession(config, listed)
        assertEquals(SessionStatus.Idle, session.state.value.status)
        assertTrue(session.state.value.messages.items.isEmpty())
        assertEquals(HistorySyncStatus.LOCAL_ONLY, session.state.value.messages.sync)
    }

    private fun descendants(): List<ProcessHandle> =
        ProcessHandle.current().descendants().use { it.toList() }

    private fun withGraph(test: suspend (DesktopGraph) -> Unit) = runBlocking {
        val graph = createGraphFactory<DesktopGraph.Factory>().create(directory.root.absolutePath)
        graph.databaseInitializer.initialize()
        // An installed CLI becomes an available engine after the user saves its settings.
        DriverManager.getConnection("jdbc:sqlite:" + directory.root.resolve("heartbeat.db")).use { db ->
            db.createStatement().use { sql ->
                sql.executeUpdate(
                    "INSERT INTO acp_connection VALUES ('dsh','DeepSeek Harness','DeepSeek','dsh'," +
                        "'--profile acp',' ','{}')",
                )
                sql.executeUpdate("INSERT INTO chat_engine VALUES ('dsh','DeepSeek Harness','dsh','{}','')")
            }
        }
        val facade = graph.aiEngineFacade
        try {
            withTimeout(10.seconds) { facade.availableEngines.first { engines -> engines.any { it.id == "dsh" } } }
            test(graph)
        } finally {
            facade.dispose()
            graph.databaseInitializer.close()
            graph.appCoroutineScope.cancel()
        }
    }
}
