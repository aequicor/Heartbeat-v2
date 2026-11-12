package io.github.aequicor.heartbeat.ai.engine.api

/**
 * Registers one agent in the catalog.
 *
 * Implementations are contributed to a Metro set; the facade receives the set and rejects duplicate
 * [EngineKey.id] values.
 */
public interface AiEngineProvider {
    public val key: EngineKey

    /**
     * Creates an engine.
     *
     * The factory allocates only: it must not start processes, open connections or reserve other
     * resources, because the facade may create a candidate and discard it during a concurrent race.
     * Resources are acquired in [AiEngine.dispatch] on [EngineEvent.Connect].
     */
    public fun create(): AiEngine
}
