package io.github.aequicor.heartbeat.ai.engine.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry point of the application to AI engines.
 *
 * Available engines form a catalog independent of connection state; [engines] is a throttled
 * snapshot for the UI. The throttle never drops the final state: a burst of transitions ends with
 * the last published state no later than one interval after the burst.
 */
public interface AiEngineFacade {
    public val availableEngines: StateFlow<List<EngineKey>>

    public val engines: StateFlow<Map<EngineKey, EngineState>>

    /**
     * Returns the engine of [key], creating it on first use.
     *
     * The key is matched by id against the catalog, so a key restored from persisted data returns
     * the catalog engine. Creation is idempotent and thread-safe.
     */
    public fun getEngine(key: EngineKey): AiEngine

    /** Disposes the engine and removes it from the runtime map; the next [getEngine] creates anew. */
    public suspend fun dispose(key: EngineKey)
}
