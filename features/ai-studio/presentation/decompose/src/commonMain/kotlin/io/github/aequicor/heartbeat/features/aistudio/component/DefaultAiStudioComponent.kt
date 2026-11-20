package io.github.aequicor.heartbeat.features.aistudio.component

import com.arkivanov.decompose.ComponentContext
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.settings.api.SettingsRoutes
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost

internal class DefaultAiStudioComponent(
    componentContext: ComponentContext,
    private val host: FeatureHost,
    store: AiStudioStore,
) : AiStudioComponent, AiStudioStore by store, ComponentContext by componentContext {
    override fun openSettings() {
        host.open(SettingsRoutes.main())
    }
}
