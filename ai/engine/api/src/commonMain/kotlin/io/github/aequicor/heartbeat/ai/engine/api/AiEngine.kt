package io.github.aequicor.heartbeat.ai.engine.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Connection to one AI agent.
 *
 * The engine owns the connection and every open runtime session. [state] is the source of truth
 * about the connection; [sessions] holds keys only, and each session publishes its own state.
 */
public interface AiEngine {
    public val key: EngineKey

    public val state: StateFlow<EngineState>

    /** Capabilities discovered from the agent handshake; empty until the engine is ready. */
    public val capabilities: StateFlow<EngineCapabilities>

    /** Keys of the open runtime sessions, in creation order. */
    public val sessions: StateFlow<List<SessionKey>>

    public fun dispatch(event: EngineEvent)

    /**
     * Returns sessions known to the agent, including persisted ones that are not open here.
     *
     * @throws AiEngineException.NotReady when the engine is not ready.
     * @throws AiEngineException.Unsupported when the agent did not advertise
     *   [EngineCapability.SESSION_LIST].
     */
    public suspend fun listSessions(cursor: String? = null): SessionPage

    /**
     * Creates a conversation, or opens [existing] when it is not `null`.
     *
     * When the session is already open, returns its runtime instance. When the agent cannot reopen
     * a persisted session, throws [AiEngineException.Unsupported] instead of starting a new
     * conversation.
     */
    public suspend fun createSession(
        config: SessionConfig,
        existing: SessionKey? = null,
    ): AiSession

    /** Returns the open session, or `null` when it is closed, unavailable or unknown. */
    public fun getSession(key: SessionKey): AiSession?

    /** Closes the runtime session; stored history is not removed. Idempotent. */
    public suspend fun closeSession(key: SessionKey)

    /** Closes every session, disconnects and releases the resources of this engine instance. */
    public suspend fun dispose()
}
