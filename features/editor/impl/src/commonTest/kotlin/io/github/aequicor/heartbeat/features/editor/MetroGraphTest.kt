package io.github.aequicor.heartbeat.features.editor

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.api.EditorControllerFactory
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.domain.repository.EditorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves that Metro contributions from `commonMain` are visible to the final
 * graph: the graph lives in the test compilation and aggregates the feature
 * bindings without any manual wiring.
 */
@DependencyGraph(AppScope::class)
internal interface EditorTestGraph {
    val editorControllerFactory: EditorControllerFactory
    val repository: EditorRepository
}

@OptIn(ExperimentalCoroutinesApi::class)
class MetroGraphTest {

    @Test
    fun contributionsReachTheGraph() = runTest {
        val graph = createGraph<EditorTestGraph>()

        assertNotNull(graph.repository)

        val controller = graph.editorControllerFactory.create("n1", this)
        advanceUntilIdle()

        assertEquals(EditorState(), controller.state.value)
    }
}
