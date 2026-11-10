package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineSessionEvent {
    public data class SendPrompt(val text: String) : AiEngineSessionEvent
    public data object Cancel : AiEngineSessionEvent
    public data class AnswerPermission(val requestId: String, val optionId: String) : AiEngineSessionEvent

    /** Loads an older partition using the current window. With no previous cursor, it does nothing. */
    public data object LoadPreviousPartition : AiEngineSessionEvent

    /** Loads a newer partition using the current window. With no next cursor, it does nothing. */
    public data object LoadNextPartition : AiEngineSessionEvent
}
