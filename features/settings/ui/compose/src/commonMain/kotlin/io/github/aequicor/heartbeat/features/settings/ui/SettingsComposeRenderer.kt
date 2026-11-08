package io.github.aequicor.heartbeat.features.settings.ui

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.settings.api.SettingsRoutes
import io.github.aequicor.heartbeat.features.settings.component.SettingsComponent
import io.github.aequicor.heartbeat.navigation.compose.ComposeFeatureRenderer
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/** Renders the settings shell root; the shell resolves it by `featureId`. */
@ContributesIntoSet(AppScope::class)
class SettingsComposeRenderer(
    private val providers: Set<ComposeSettingsPageProvider>,
) : ComposeFeatureRenderer {

    override val featureId: String = SettingsRoutes.FEATURE_ID

    @Composable
    override fun Render(component: FeatureRoot) {
        require(component is SettingsComponent) {
            "Unexpected settings component: ${component::class}"
        }

        SettingsScreen(component = component, providers = providers)
    }
}
