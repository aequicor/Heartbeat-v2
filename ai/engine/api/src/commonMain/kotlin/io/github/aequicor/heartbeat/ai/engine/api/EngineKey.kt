package io.github.aequicor.heartbeat.ai.engine.api

/**
 * Stable identity of an AI engine.
 *
 * Identity is [id] only: [name], [description] and [connection] are display metadata. A key built
 * from persisted data therefore matches the catalog entry even when the metadata changed, and
 * `Map<EngineKey, ...>` cannot be split by a metadata edit.
 *
 * The key never contains credentials, executable paths or other secrets.
 */
public class EngineKey(
    public val id: String,
    public val name: String,
    public val description: String,
    public val connection: EngineConnection,
) {
    init {
        require(id.isNotBlank()) { "EngineKey.id must not be blank" }
    }

    override fun equals(other: Any?): Boolean = other is EngineKey && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "EngineKey(id=$id)"
}

/** How an engine reaches its agent: an external protocol server or an in-process runtime. */
public enum class EngineConnection {
    ACP,
    EMBEDDED,
}
