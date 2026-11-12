package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface SessionEvent {
    public data class SendPrompt(val text: String) : SessionEvent
    public data object Cancel : SessionEvent
    public data class AnswerPermission(val requestId: String, val optionId: String) : SessionEvent

    /** Loads an older partition using the current window. With no previous cursor, it does nothing. */
    public data object LoadPreviousPartition : SessionEvent

    /** Loads a newer partition using the current window. With no next cursor, it does nothing. */
    public data object LoadNextPartition : SessionEvent
}
