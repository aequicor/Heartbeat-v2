package io.github.aequicor.heartbeat

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioSettings
import io.github.aequicor.heartbeat.features.editor.api.EditorStoreFactory
import io.github.aequicor.heartbeat.features.editor.component.EditorComponentFactory
import io.github.aequicor.heartbeat.features.settings.api.SettingsRegistry
import io.github.aequicor.heartbeat.features.settings.component.SettingsComponentFactory
import io.github.aequicor.heartbeat.navigation.compose.ComposeFeatureRenderer
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider
import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry
import io.github.aequicor.heartbeat.shell.ShellComponentFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compile-time integration test of the composition root (HB-ARCH-014).
 *
 * The graph requests the feature entry points contributed by different modules: the store
 * factory comes from `features:editor:impl`, the Decompose adapter from
 * `features:editor:presentation:decompose`, and the navigation sets from the feature modules
 * themselves. Metro resolves everything while compiling this test, so a missing or conflicting
 * binding fails the build instead of the platform entry point.
 */
@DependencyGraph(AppScope::class)
internal interface DesktopCompositionRootGraph {
    val editorStoreFactory: EditorStoreFactory
    val editorComponentFactory: EditorComponentFactory
    val shellComponentFactory: ShellComponentFactory
    val settingsRegistry: SettingsRegistry
    val settingsComponentFactory: SettingsComponentFactory
    val settingsPageProviders: Set<ComposeSettingsPageProvider>
    val aiStudioSettings: AiStudioSettings
    val featureEntries: Set<DecomposeFeatureEntry>
    val featureRenderers: Set<ComposeFeatureRenderer>
}

class CompositionRootGraphTest {

    @Test
    fun graphResolvesFeatureEntryPoints() {
        val graph = createGraph<DesktopCompositionRootGraph>()

        assertNotNull(graph.editorStoreFactory)
        assertNotNull(graph.editorComponentFactory)
        assertNotNull(graph.shellComponentFactory)
        assertNotNull(graph.settingsRegistry)
        assertNotNull(graph.settingsComponentFactory)
        assertNotNull(graph.aiStudioSettings)
    }

    @Test
    fun navigationSetsAreAggregatedFromFeatureModules() {
        val graph = createGraph<DesktopCompositionRootGraph>()

        assertTrue(graph.featureEntries.any { it.featureId == "ai-studio" })
        assertTrue(graph.featureEntries.any { it.featureId == "settings" })
        assertTrue(graph.featureRenderers.any { it.featureId == "ai-studio" })
        assertTrue(graph.featureRenderers.any { it.featureId == "settings" })
    }

    @Test
    fun settingsRegistryAggregatesFeaturePages() {
        val graph = createGraph<DesktopCompositionRootGraph>()

        assertTrue(graph.settingsRegistry.pages.any { it.id.value == "ai-studio" })
        assertTrue(graph.settingsPageProviders.any { "ai-studio" in it.pageIds })
    }
}
