package io.github.aequicor.heartbeat.navigation.compose

import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/**
 * Renders the root component of a feature. The shell composes the renderer of the active route and
 * never lists feature screens.
 */
interface ComposeFeatureRenderer {

    val featureId: String

    /** Complete or confirm pending edits before switching to a different workspace. */
    @Throwable(CancellationException::class)
    suspend fun requestLeave(component: FeatureRoot, leave: () -> Unit) = leave()

    @Composable
    fun Render(component: FeatureRoot)
}
