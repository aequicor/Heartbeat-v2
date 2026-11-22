package io.github.aequicor.heartbeat.features.aienginefacade.component.engines

import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState
import kotlinx.coroutines.flow.StateFlow
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

public interface EngineSettingsComponent {

    public val state: StateFlow<EngineSettingsScreenState>

    @Throwable(CancellationException::class)
    public suspend fun dispatch(action: EngineSettingsScreenAction)
}
