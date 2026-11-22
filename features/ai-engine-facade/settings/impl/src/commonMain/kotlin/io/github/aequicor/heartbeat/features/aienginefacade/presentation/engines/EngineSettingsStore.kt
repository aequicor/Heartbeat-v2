package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

import kotlinx.coroutines.flow.StateFlow
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

public interface EngineSettingsStore {

    public val state: StateFlow<EngineSettingsScreenState>

    @Throwable(CancellationException::class)
    public suspend fun dispatch(action: EngineSettingsScreenAction)
}
