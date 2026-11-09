package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineEvent {
    public data object Connect : AiEngineEvent
    public data object Disconnect : AiEngineEvent
    public data class AuthorizationCompleted(val result: AiEngineAuthResult) : AiEngineEvent
}
