package io.github.aequicor.heartbeat.ai.engine.api

/**
 * Identity of a runtime conversation with one engine.
 *
 * Identity is [engine] id plus [id]: [title], [workingDirectory] and [updatedAt] are display
 * metadata. Two keys for the same conversation stay equal after a title or timestamp update, so a
 * map keyed by `SessionKey` cannot be split by an agent-side metadata change.
 */
public class SessionKey(
    public val engine: EngineKey,
    public val id: String,
    public val title: String? = null,
    public val workingDirectory: String? = null,
    public val updatedAt: String? = null,
) {
    init {
        require(id.isNotBlank()) { "SessionKey.id must not be blank" }
    }

    /** Returns a key with new display metadata; identity is preserved. */
    public fun withMetadata(
        title: String? = this.title,
        workingDirectory: String? = this.workingDirectory,
        updatedAt: String? = this.updatedAt,
    ): SessionKey = SessionKey(engine, id, title, workingDirectory, updatedAt)

    override fun equals(other: Any?): Boolean =
        other is SessionKey && other.engine.id == engine.id && other.id == id

    override fun hashCode(): Int = 31 * engine.id.hashCode() + id.hashCode()

    override fun toString(): String = "SessionKey(engine=${engine.id}, id=$id)"
}

/** Parameters of a conversation: the workspace the agent works in. */
public data class SessionConfig(
    val workingDirectory: String? = null,
)

/** One page of sessions known to the agent; [nextCursor] is `null` on the last page. */
public data class SessionPage(
    val sessions: List<SessionKey>,
    val nextCursor: String? = null,
)
