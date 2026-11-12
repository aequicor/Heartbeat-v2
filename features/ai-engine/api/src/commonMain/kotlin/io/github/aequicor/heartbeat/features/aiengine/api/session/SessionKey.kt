package io.github.aequicor.heartbeat.features.aiengine.api.session

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey

/** Identifies a session by engine ID and session ID. Metadata can change independently. */
public class SessionKey(
    public val engine: AiEngineKey,
    public val id: String,
    public val title: String? = null,
    public val workingDirectory: String? = null,
    public val updatedAt: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is SessionKey && engine.id == other.engine.id && id == other.id

    override fun hashCode(): Int = 31 * engine.id.hashCode() + id.hashCode()
}
