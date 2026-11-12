package io.github.aequicor.heartbeat.features.aiengine.api

import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionPage
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import kotlinx.coroutines.flow.StateFlow

public interface AiEngine {
    public val key: AiEngineKey

    /** The current state of this engine. */
    public val state: StateFlow<AiEngineState>

    /** Current statuses of open runtime sessions, indexed by session key. */
    public val sessions: StateFlow<Map<SessionKey, SessionStatus>>

    public fun dispatch(event: AiEngineEvent)

    /** Lists sessions matching [config], including sessions that are not open. */
    public suspend fun getListSessions(config: SessionListConfig): SessionPage

    /**
     * Creates a session when [existing] is `null`. With a key, returns the open runtime if present
     * or reopens the specified session. Reports an error if it cannot reopen that session.
     */
    public suspend fun getOrCreateSession(
        config: SessionConfig,
        existing: SessionKey? = null,
    ): Session

    public suspend fun closeSession(key: SessionKey)

    /** Releases the connection and open runtime sessions. Cleanup can be retried after an error. */
    public suspend fun dispose()
}
