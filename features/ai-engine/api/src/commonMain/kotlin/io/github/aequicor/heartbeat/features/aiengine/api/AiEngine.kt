package io.github.aequicor.heartbeat.features.aiengine.api

import kotlinx.coroutines.flow.StateFlow

public interface AiEngine {
    public val key: AiEngineKey

    /** The current state of this engine. */
    public val state: StateFlow<AiEngineState>

    /** Snapshots of open runtime sessions. */
    public val sessions: StateFlow<List<AiEngineSessionSnapshot>>

    public fun dispatch(event: AiEngineEvent)

    /** Lists sessions known to the engine, including sessions that are not open. */
    public suspend fun listSessions(cursor: String? = null): AiEngineSessionPage

    /**
     * Creates a session when [existing] is `null`. Otherwise opens the specified session or returns
     * its open runtime. An engine that cannot reopen the requested session reports an error.
     */
    public suspend fun createSession(
        config: AiEngineSessionConfig,
        existing: AiEngineSessionKey? = null,
    ): AiEngineSession

    public fun getSession(key: AiEngineSessionKey): AiEngineSession?

    public suspend fun closeSession(key: AiEngineSessionKey)

    /** Releases the connection and open runtime sessions. Cleanup can be retried after an error. */
    public suspend fun dispose()
}
