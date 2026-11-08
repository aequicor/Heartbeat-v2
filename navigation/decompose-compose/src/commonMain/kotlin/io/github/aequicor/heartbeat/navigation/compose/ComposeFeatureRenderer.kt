package io.github.aequicor.heartbeat.navigation.compose

import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/**
 * Renders the root component of a feature. The shell composes the renderer of the active route and
 * never lists feature screens.
 */
interface ComposeFeatureRenderer {

    val featureId: String

    @Composable
    fun Render(component: FeatureRoot)
}
