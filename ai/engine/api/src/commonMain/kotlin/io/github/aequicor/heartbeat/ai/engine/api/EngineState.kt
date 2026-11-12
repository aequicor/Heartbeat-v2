package io.github.aequicor.heartbeat.ai.engine.api

/** Connection state of one engine; the engine owns this state, the facade only aggregates it. */
public sealed interface EngineState {
    public data object NotInitialized : EngineState

    public data object Connecting : EngineState

    /** The agent requires the user to finish authorization before the connection can proceed. */
    public data class AuthorizationRequired(
        val authMethodId: String,
        val label: String,
    ) : EngineState

    public data object Ready : EngineState

    public data class Failed(
        val reason: String,
    ) : EngineState
}

/** Commands that change the connection state. */
public sealed interface EngineEvent {
    public data object Connect : EngineEvent

    public data object Disconnect : EngineEvent

    public data class AuthorizationCompleted(
        val result: AuthResult,
    ) : EngineEvent
}

/** Result reported by the authorization flow; success still requires a completed connect. */
public sealed interface AuthResult {
    public data object Success : AuthResult

    public data object Cancelled : AuthResult

    public data class Failure(
        val reason: String,
    ) : AuthResult
}
