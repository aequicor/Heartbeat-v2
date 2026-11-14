package io.github.aequicor.heartbeat.features.aistudio.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStoreFactory
import io.github.aequicor.heartbeat.features.settings.api.SettingsRoutes
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class DefaultAiStudioComponent(
    componentContext: ComponentContext,
    private val host: FeatureHost,
    factory: AiStudioStoreFactory,
    dispatcher: CoroutineDispatcher,
) : AiStudioComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    override val store = factory.create(scope)

    init {
        componentContext.lifecycle.doOnDestroy(scope::cancel)
    }

    override fun openSettings() {
        host.open(SettingsRoutes.main())
    }
}
