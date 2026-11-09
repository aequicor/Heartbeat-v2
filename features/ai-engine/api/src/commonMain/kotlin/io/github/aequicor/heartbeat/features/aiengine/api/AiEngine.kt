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

public sealed interface AiEngineState {
    public data object NotInitialized : AiEngineState
    public data object Connecting : AiEngineState
    public data class AuthorizationRequired(val authFeatureId: String) : AiEngineState
    public data object Ready : AiEngineState
    public data class Failed(val reason: String) : AiEngineState
}

public sealed interface AiEngineEvent {
    public data object Connect : AiEngineEvent
    public data object Disconnect : AiEngineEvent
    public data class AuthorizationCompleted(val result: AiEngineAuthResult) : AiEngineEvent
}

public sealed interface AiEngineAuthResult {
    public data object Success : AiEngineAuthResult
    public data object Cancelled : AiEngineAuthResult
    public data class Failure(val reason: String) : AiEngineAuthResult
}
