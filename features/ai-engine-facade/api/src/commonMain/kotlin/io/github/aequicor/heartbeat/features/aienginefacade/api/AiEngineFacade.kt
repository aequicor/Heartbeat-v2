package io.github.aequicor.heartbeat.features.aienginefacade.api

import kotlinx.coroutines.flow.StateFlow

public interface AiEngineFacade {

    /** Registered engine keys, including engines that have no runtime instance. */
    public val availableEngines: StateFlow<List<AiEngineFacadeEngineKey>>

    /** Aggregated states. Changes can take up to 500 ms to appear while observation is active. */
    public val engines: StateFlow<Map<AiEngineFacadeEngineKey, AiEngineFacadeEngineState>>

    /**
     * Returns the engine for [key]'s ID and creates it on first access.
     *
     * @throws IllegalArgumentException if the ID is unknown.
     * @throws IllegalStateException if engine disposal is pending or the facade is closed.
     */
    public fun getEngine(key: AiEngineFacadeEngineKey): AiEngineFacadeEngine

    /** Releases the runtime for [key]'s ID, if present. Retry this call if cleanup fails. */
    public suspend fun dispose(key: AiEngineFacadeEngineKey)

    /**
     * Closes the facade and releases all engine runtimes. Call before ending the owning scope.
     * If cleanup fails, call again to release the remaining runtimes.
     */
    public suspend fun dispose()
}
