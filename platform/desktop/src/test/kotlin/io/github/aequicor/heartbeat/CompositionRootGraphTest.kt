package io.github.aequicor.heartbeat

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.api.EditorStoreFactory
import io.github.aequicor.heartbeat.features.editor.component.EditorComponentFactory
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Compile-time integration test of the composition root (HB-ARCH-014).
 *
 * The graph requests the editor entry points contributed by two different modules: the store
 * factory comes from `features:editor:impl`, the component factory from
 * `features:editor:presentation:decompose`. Metro resolves both while compiling this test, so a
 * missing or conflicting binding fails the build instead of the platform entry point.
 */
@DependencyGraph(AppScope::class)
internal interface DesktopCompositionRootGraph {
    val editorStoreFactory: EditorStoreFactory
    val editorComponentFactory: EditorComponentFactory
}

class CompositionRootGraphTest {

    @Test
    fun graphResolvesFeatureEntryPoints() {
        val graph = createGraph<DesktopCompositionRootGraph>()

        assertNotNull(graph.editorStoreFactory)
        assertNotNull(graph.editorComponentFactory)
    }
}
