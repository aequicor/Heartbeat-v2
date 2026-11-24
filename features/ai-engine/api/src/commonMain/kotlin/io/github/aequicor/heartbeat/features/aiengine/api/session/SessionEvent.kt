package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface SessionEvent {
    public data class SendPrompt(
        val text: String,
        val turnId: String? = null,
        val attachments: List<String> = listOf(),
        val promptFiles: List<PromptFile> = listOf(),
    ) : SessionEvent

    public sealed interface PromptFile {
        public data class Text(val content: String) : PromptFile
        public data class Image(val mimeType: String, val base64: String) : PromptFile
    }
    public data object Cancel : SessionEvent
    public data class AnswerPermission(val requestId: String, val optionId: String) : SessionEvent

    /** Loads an older partition using the current window. With no previous cursor, it does nothing. */
    public data object LoadPreviousPartition : SessionEvent

    /** Loads a newer partition using the current window. With no next cursor, it does nothing. */
    public data object LoadNextPartition : SessionEvent
}
