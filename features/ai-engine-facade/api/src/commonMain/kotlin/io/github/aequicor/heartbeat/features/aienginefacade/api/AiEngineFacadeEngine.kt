package io.github.aequicor.heartbeat.features.aienginefacade.api

import kotlinx.coroutines.flow.StateFlow

public interface AiEngineFacadeEngine {
    public val key: AiEngineFacadeEngineKey

    /** The current state of this engine. */
    public val state: StateFlow<AiEngineFacadeEngineState>

    /** Snapshots of open runtime sessions. */
    public val sessions: StateFlow<List<AiEngineFacadeSessionSnapshot>>

    public fun dispatch(event: AiEngineFacadeEngineEvent)

    /** Lists sessions known to the engine, including sessions that are not open. */
    public suspend fun listSessions(cursor: String? = null): AiEngineFacadeSessionPage

    /**
     * Creates a session when [existing] is `null`. Otherwise opens the specified session or returns
     * its open runtime. An engine that cannot reopen the requested session reports an error.
     */
    public suspend fun createSession(
        config: AiEngineFacadeSessionConfig,
        existing: AiEngineFacadeSessionKey? = null,
    ): AiEngineFacadeSession

    public fun getSession(key: AiEngineFacadeSessionKey): AiEngineFacadeSession?

    public suspend fun closeSession(key: AiEngineFacadeSessionKey)

    /** Releases the connection and open runtime sessions. Cleanup can be retried after an error. */
    public suspend fun dispose()
}

public sealed interface AiEngineFacadeEngineState {
    public data object NotInitialized : AiEngineFacadeEngineState
    public data object Connecting : AiEngineFacadeEngineState
    public data class AuthorizationRequired(val authFeatureId: String) : AiEngineFacadeEngineState
    public data object Ready : AiEngineFacadeEngineState
    public data class Failed(val reason: String) : AiEngineFacadeEngineState
}

public sealed interface AiEngineFacadeEngineEvent {
    public data object Connect : AiEngineFacadeEngineEvent
    public data object Disconnect : AiEngineFacadeEngineEvent
    public data class AuthorizationCompleted(val result: AiEngineFacadeAuthResult) : AiEngineFacadeEngineEvent
}

public sealed interface AiEngineFacadeAuthResult {
    public data object Success : AiEngineFacadeAuthResult
    public data object Cancelled : AiEngineFacadeAuthResult
    public data class Failure(val reason: String) : AiEngineFacadeAuthResult
}
