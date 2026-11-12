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
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DesktopDshGraphTest {
    @Test
    fun registersDshInDesktopEngineCatalog() = withFacade { facade ->
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

        withFacade { facade ->
            withTimeout(30.seconds) {
                val existingProcesses = descendants().toSet()
                val key = facade.availableEngines.value.single { it.id == "dsh" }
                val engine = facade.getEngine(key)
                engine.dispatch(AiEngineEvent.Connect)
                val connected = engine.state.first { it == AiEngineState.Ready || it is AiEngineState.Failed }
                assertEquals(AiEngineState.Ready, connected)

                verifySessionResume(engine, workspace)
                val processes = descendants() - existingProcesses
                val minimumCount = if (System.getProperty("os.name").startsWith("Windows")) 2 else 1
                assertTrue(processes.size >= minimumCount)

                engine.dispatch(AiEngineEvent.Disconnect)
                engine.state.first { it == AiEngineState.NotInitialized }
                processes.forEach { it.onExit().await() }
            }
        }
    }

    private suspend fun verifySessionResume(engine: AiEngine, workspace: Path) {
        val created = engine.getOrCreateSession(SessionConfig(workspace.toString()))
        engine.closeSession(created.key)

        val project = SessionListConfig.ByProject("test-project", workingDirectory = workspace.toString())
        val listed = engine.getListSessions(project).sessions.single()
        assertEquals(created.key, listed)
        val otherWorkspace = Files.createDirectories(workspace.resolveSibling("other-workspace"))
        val otherProject = project.copy(workingDirectory = otherWorkspace.toString())
        assertTrue(engine.getListSessions(otherProject).sessions.isEmpty())

        val resumed = engine.getOrCreateSession(SessionConfig(), listed)
        assertEquals(created.key, resumed.key)
        assertEquals(HistorySyncStatus.LOCAL_ONLY, resumed.state.value.messages.sync)
        assertTrue(resumed.state.value.messages.items.isEmpty())
    }

    private fun descendants(): List<ProcessHandle> =
        ProcessHandle.current().descendants().use { it.toList() }

    private fun withFacade(test: suspend (AiEngineFacade) -> Unit) = runBlocking {
        val graph = createGraphFactory<DesktopGraph.Factory>().create("")
        val facade = graph.aiEngineFacade
        try {
            test(facade)
        } finally {
            facade.dispose()
            graph.appCoroutineScope.cancel()
        }
    }
}
