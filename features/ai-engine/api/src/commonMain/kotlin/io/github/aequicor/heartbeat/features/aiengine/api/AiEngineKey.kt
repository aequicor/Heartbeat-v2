package io.github.aequicor.heartbeat.features.aiengine.api

/** A catalog key with a stable ID. Keys with different IDs must compare unequal. */
public interface AiEngineKey {
    public val id: String
    public val name: String
    public val description: String
    public val connection: AiEngineConnection
}
