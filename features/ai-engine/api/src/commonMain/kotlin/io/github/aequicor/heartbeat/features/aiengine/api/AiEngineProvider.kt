package io.github.aequicor.heartbeat.features.aiengine.api

public interface AiEngineProvider {
    public val key: AiEngineKey

    /** Creates a runtime for [key] without opening a connection. */
    public fun create(): AiEngine
}
