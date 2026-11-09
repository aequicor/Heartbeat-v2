package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineSessionEvent {
    public data class SendPrompt(val text: String) : AiEngineSessionEvent
    public data object Cancel : AiEngineSessionEvent
    public data class AnswerPermission(val requestId: String, val optionId: String) : AiEngineSessionEvent
}
