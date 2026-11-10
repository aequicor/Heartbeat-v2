package io.github.aequicor.heartbeat.features.aiengine.api.session

/** Selects a page of sessions. Use a cursor returned for the same project or parent. */
public sealed interface SessionListConfig {
    public val cursor: String?

    /** Lists root sessions in the specified project. */
    public data class ByProject(
        val projectId: String,
        override val cursor: String? = null,
    ) : SessionListConfig

    /** Lists direct children of the specified session. */
    public data class ByParent(
        val parentSessionKey: SessionKey,
        override val cursor: String? = null,
    ) : SessionListConfig
}
