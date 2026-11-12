package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineAuthResult {
    public data object Success : AiEngineAuthResult
    public data object Cancelled : AiEngineAuthResult
    public data class Failure(val reason: String) : AiEngineAuthResult
}
