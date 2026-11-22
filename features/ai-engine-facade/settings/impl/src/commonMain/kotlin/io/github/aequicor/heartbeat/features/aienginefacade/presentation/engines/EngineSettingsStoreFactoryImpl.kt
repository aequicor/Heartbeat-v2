package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.features.aienginefacade.domain.interactor.EngineSettingsInteractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
internal class EngineSettingsStoreFactoryImpl(
    private val interactor: EngineSettingsInteractor,
    @Named("engine-settings-default") private val defaultDispatcher: CoroutineDispatcher,
) : EngineSettingsStoreFactory {

    override fun create(
        scope: CoroutineScope,
        ignoreEnvironmentNameCase: Boolean,
    ): EngineSettingsStore {
        val store = EngineSettingsStoreImpl(scope, defaultDispatcher, interactor, ignoreEnvironmentNameCase)
        scope.launch { store.dispatch(EngineSettingsScreenAction.Load) }
        return store
    }
}
