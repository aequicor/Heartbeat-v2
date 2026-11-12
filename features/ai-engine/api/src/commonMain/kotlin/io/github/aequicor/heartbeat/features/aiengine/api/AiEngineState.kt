package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineState {
    public data object NotInitialized : AiEngineState
    public data object Connecting : AiEngineState
    public data class AuthorizationRequired(val authFeatureId: String) : AiEngineState
    public data object Ready : AiEngineState
    public data class Failed(val reason: String) : AiEngineState
}
