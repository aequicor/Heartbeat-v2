package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

import kotlinx.coroutines.CoroutineScope

public fun interface EngineSettingsStoreFactory {

    public fun create(scope: CoroutineScope, ignoreEnvironmentNameCase: Boolean, close: () -> Unit): EngineSettingsStore
}
