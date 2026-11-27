package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface SessionEvent {
    public data class SendPrompt(
        val text: String,
        val turnId: String? = null,
        val attachments: List<String> = listOf(),
        val promptFiles: List<PromptFile> = listOf(),
        val validatedSettings: SessionSettings? = null,
    ) : SessionEvent

    public sealed interface PromptFile {

        public data class Text(val content: String) : PromptFile

        public data class Image(val mimeType: String, val base64: String) : PromptFile

        public data class Resource(
            val uri: String,
            val name: String,
            val content: Content,
            val mimeType: String? = null,
            val description: String? = null,
        ) : PromptFile {

            public sealed interface Content {

                public data class Text(val value: String) : Content

                public data class Binary(val base64: String) : Content
            }
        }

        /** The URI resolves in the agent's environment. */
        public data class ResourceLink(
            val uri: String,
            val name: String,
            val title: String = name,
            val mimeType: String? = null,
            val sizeBytes: Long? = null,
            val description: String? = null,
        ) : PromptFile
    }
    public data object Cancel : SessionEvent
    public data class AnswerPermission(val requestId: String, val optionId: String) : SessionEvent
    public data class AnswerQuestionnaire(val requestId: String, val answer: QuestionnaireAnswer) : SessionEvent

    /** Loads an older partition using the current window. With no previous cursor, it does nothing. */
    public data object LoadPreviousPartition : SessionEvent

    /** Loads a newer partition using the current window. With no next cursor, it does nothing. */
    public data object LoadNextPartition : SessionEvent
}
