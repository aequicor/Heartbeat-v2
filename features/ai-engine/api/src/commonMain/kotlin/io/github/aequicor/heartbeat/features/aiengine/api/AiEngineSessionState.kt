package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineSessionState {
    public data object Idle : AiEngineSessionState
    public data object Running : AiEngineSessionState
    public data class AwaitingPermission(val requestId: String, val description: String) : AiEngineSessionState
    public data object Unavailable : AiEngineSessionState
    public data object Closed : AiEngineSessionState
    public data class Failed(val reason: String) : AiEngineSessionState
}
