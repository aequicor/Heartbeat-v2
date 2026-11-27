package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal sealed interface SentMessageAttachment {

    val metadata: SentAttachmentMetadata
    val uri: String

    data class Resource(
        override val metadata: SentAttachmentMetadata,
        override val uri: String,
        val content: Content,
    ) : SentMessageAttachment {

        sealed interface Content {

            data class Text(val value: String) : Content

            data class Binary(val base64: String) : Content
        }
    }

    data class ResourceLink(
        override val metadata: SentAttachmentMetadata,
        override val uri: String,
    ) : SentMessageAttachment
}
