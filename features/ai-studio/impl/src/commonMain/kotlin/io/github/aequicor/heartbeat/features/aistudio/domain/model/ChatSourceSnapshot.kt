package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatSourceSnapshot(
    val source: ChatSourceReference,
    val role: Role,
    val agentName: String?,
    val fragments: List<Fragment>,
    val attachments: List<Attachment> = emptyList(),
) {

    val originalText: String
        get() = fragments.joinToString(separator = "") { it.text }

    enum class Role { User, Agent }

    data class Fragment(val text: String, val format: Format, val formattedMarkdown: String? = null) {

        enum class Format { Plain, Markdown, Code, Diff }
    }

    data class Attachment(
        val versionId: String,
        val metadata: SentAttachmentMetadata,
        val transferMode: AttachmentTransferMode,
        val uri: String?,
        val content: SentMessageAttachment.Resource.Content?,
        val thumbnail: String? = null,
    )
}
