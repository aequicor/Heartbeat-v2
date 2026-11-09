package io.github.aequicor.heartbeat.features.aienginefacade.api

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import kotlinx.coroutines.flow.StateFlow

public interface AiEngineFacade {

    /** Registered engine keys, including engines that have no runtime instance. */
    public val availableEngines: StateFlow<List<AiEngineKey>>

    /** Aggregated states. Changes can take up to 500 ms to appear while observation is active. */
    public val engines: StateFlow<Map<AiEngineKey, AiEngineState>>

    /**
     * Returns the engine for [key]'s ID and creates it on first access.
     *
     * @throws IllegalArgumentException if the ID is unknown.
     * @throws IllegalStateException if engine disposal is pending or the facade is closed.
     */
    public fun getEngine(key: AiEngineKey): AiEngine

    /** Releases the runtime for [key]'s ID, if present. Retry this call if cleanup fails. */
    public suspend fun dispose(key: AiEngineKey)

    /**
     * Closes the facade and releases all engine runtimes. Call before ending the owning scope.
     * If cleanup fails, call again to release the remaining runtimes.
     */
    public suspend fun dispose()
}
