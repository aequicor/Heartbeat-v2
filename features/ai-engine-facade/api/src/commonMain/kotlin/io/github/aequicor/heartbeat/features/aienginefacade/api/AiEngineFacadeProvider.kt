package io.github.aequicor.heartbeat.features.aienginefacade.api

/** A catalog key with a stable ID. Keys with different IDs must compare unequal. */
public interface AiEngineFacadeEngineKey {
    public val id: String
    public val name: String
    public val description: String
    public val connection: AiEngineFacadeEngineConnection
}

public enum class AiEngineFacadeEngineConnection {
    ACP,
    EMBEDDED,
}

public interface AiEngineFacadeProvider {
    public val key: AiEngineFacadeEngineKey

    /** Creates a runtime for [key] without opening a connection. */
    public fun create(): AiEngineFacadeEngine
}
