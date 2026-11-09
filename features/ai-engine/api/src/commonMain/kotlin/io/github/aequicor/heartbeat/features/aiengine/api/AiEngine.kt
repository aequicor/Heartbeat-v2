package io.github.aequicor.heartbeat.features.aiengine.api

import kotlinx.coroutines.flow.StateFlow

public interface AiEngine {
    public val key: AiEngineKey

    /** The current state of this engine. */
    public val state: StateFlow<AiEngineState>

    /** Current states of open runtime sessions, indexed by session key. */
    public val sessions: StateFlow<Map<AiEngineSessionKey, AiEngineSessionState>>

    public fun dispatch(event: AiEngineEvent)

    /** Lists sessions known to the engine, including sessions that are not open. */
    public suspend fun listSessions(cursor: String? = null): AiEngineSessionPage

    /**
     * Creates a session when [existing] is `null`. With a key, returns the open runtime if present
     * or reopens the specified session. Reports an error if it cannot reopen that session.
     */
    public suspend fun getOrCreateSession(
        config: AiEngineSessionConfig,
        existing: AiEngineSessionKey? = null,
    ): AiEngineSession

    public suspend fun closeSession(key: AiEngineSessionKey)

    /** Releases the connection and open runtime sessions. Cleanup can be retried after an error. */
    public suspend fun dispose()
}
