package io.github.aequicor.heartbeat

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.di.DesktopGraph
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.features.aistudio.component.AiStudioComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DesktopDshGraphTest {
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
            val lifecycle = LifecycleRegistry()
            lifecycle.resume()
            try {
                val shell = graph.shellComponentFactory.create(
                    componentContext = DefaultComponentContext(lifecycle),
                    initialRoute = AiStudioRoutes.main(),
                    dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
                )
                val studio = assertIs<AiStudioComponent>(shell.childStack.value.active.instance).store
                withTimeout(30.seconds) { verifyStudioConnection(graph, studio, workspace) }
            } finally {
                lifecycle.destroy()
            }
        }
    }

    private suspend fun verifyStudioConnection(graph: DesktopGraph, studio: AiStudioStore, workspace: Path) {
        val existingProcesses = descendants().toSet()
        studio.dispatch(AiStudioAction.EditDirectory(workspace.toString()))
        studio.dispatch(AiStudioAction.Connect)
        val connection = studio.screenState.first {
            !it.busy && (it.connected || it.error != null || it.connection is AiEngineState.Failed)
        }
        assertTrue(connection.connected, "Studio connection failed: ${connection.error ?: connection.connection}")
        val facade = graph.aiEngineFacade
        val engine = facade.getEngine(facade.availableEngines.value.single { it.id == "dsh" })
        verifySessionResume(engine, studio, workspace)

        val processes = descendants() - existingProcesses
        val minimumCount = if (System.getProperty("os.name").startsWith("Windows")) 2 else 1
        assertTrue(processes.size >= minimumCount)
        studio.dispatch(AiStudioAction.Disconnect)
        studio.screenState.first { !it.busy && it.connection == AiEngineState.NotInitialized }
        processes.forEach { it.onExit().await() }
    }

    private suspend fun verifySessionResume(engine: AiEngine, studio: AiStudioStore, workspace: Path) {
        studio.dispatch(AiStudioAction.NewChat)
        val created = checkNotNull(studio.screenState.first { !it.busy && it.selected != null }.selected)
        engine.closeSession(created)
        studio.screenState.first { it.sessionStatus == SessionStatus.Closed }

        val project = SessionListConfig.ByProject("test-project", workingDirectory = workspace.toString())
        val listed = engine.getListSessions(project).sessions.single()
        assertEquals(created, listed)
        val otherWorkspace = Files.createDirectories(workspace.resolveSibling("other-workspace"))
        val otherProject = project.copy(workingDirectory = otherWorkspace.toString())
        assertTrue(engine.getListSessions(otherProject).sessions.isEmpty())

        studio.dispatch(AiStudioAction.OpenChat(listed))
        val resumed = studio.screenState.first { !it.busy && it.sessionStatus == SessionStatus.Idle }
        assertEquals(created, resumed.selected)
        assertTrue(resumed.timeline.isEmpty())
        val session = engine.getOrCreateSession(SessionConfig(workspace.toString()), listed)
        assertEquals(HistorySyncStatus.LOCAL_ONLY, session.state.value.messages.sync)
    }

    private fun descendants(): List<ProcessHandle> =
        ProcessHandle.current().descendants().use { it.toList() }

    private fun withGraph(test: suspend CoroutineScope.(DesktopGraph) -> Unit) = runBlocking {
        val graph = createGraphFactory<DesktopGraph.Factory>().create("")
        val facade = graph.aiEngineFacade
        try {
            test(graph)
        } finally {
            facade.dispose()
            graph.appCoroutineScope.cancel()
        }
    }
}
