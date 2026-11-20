package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.features.aistudio.component.AiStudioComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioSettings
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.loadAuroraBackdrop
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
        require(component is AiStudioComponent && component is AiStudioStore) {
            "Unexpected AI studio component: ${component::class}"
        }

        val avatarVisible by settings.avatarVisible.collectAsState()
        val state by component.screenState.collectAsState()
        val backdrop by produceState<AuroraBackdrop?>(null) { value = loadAuroraBackdrop() }
        AiStudioScreen(
            state = state,
            draft = component.draft,
            liveText = component.liveText,
            backdrop = backdrop,
            onAction = component::dispatch,
            avatarVisible = avatarVisible,
            onOpenSettings = component::openSettings,
        )
    }
}
