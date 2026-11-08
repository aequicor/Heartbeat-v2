package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioSettings
import io.github.aequicor.heartbeat.features.aistudio.component.AiStudioComponent
import io.github.aequicor.heartbeat.navigation.compose.ComposeFeatureRenderer
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/** Renders the AI studio workspace root; the shell resolves it by `featureId`. */
@ContributesIntoSet(AppScope::class)
class AiStudioComposeRenderer(
    private val settings: AiStudioSettings,
) : ComposeFeatureRenderer {

    override val featureId: String = AiStudioRoutes.FEATURE_ID

    @Composable
    override fun Render(component: FeatureRoot) {
        require(component is AiStudioComponent) {
            "Unexpected AI studio component: ${component::class}"
        }

        val avatarVisible by settings.avatarVisible.collectAsState()

        AiStudioScreen(
            avatarVisible = avatarVisible,
            onOpenSettings = component::openSettings,
        )
    }
}
