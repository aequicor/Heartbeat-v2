package io.github.aequicor.heartbeat.features.aistudio.component

import com.arkivanov.decompose.ComponentContext
import io.github.aequicor.heartbeat.features.settings.api.SettingsRoutes
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost

/**
 * The workspace has no store yet: the screen renders the agreed layout with sample content. The
 * component exists so the shell owns a feature root and the renderer can bind the screen without
 * the shell knowing it. The host opens the settings shell when the user asks for it.
 */
internal class DefaultAiStudioComponent(
    componentContext: ComponentContext,
    private val host: FeatureHost,
) : AiStudioComponent, ComponentContext by componentContext {

    override fun openSettings() {
        host.open(SettingsRoutes.main())
    }
}
