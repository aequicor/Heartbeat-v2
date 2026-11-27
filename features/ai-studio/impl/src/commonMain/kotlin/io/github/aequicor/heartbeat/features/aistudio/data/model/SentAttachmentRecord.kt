package io.github.aequicor.heartbeat.features.aistudio.data.model

import io.github.aequicor.heartbeat.features.aistudio.domain.model.SentAttachmentMetadata
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SentMessageAttachment
import kotlin.io.encoding.Base64

internal data class SentAttachmentRecord(
    val mode: String,
    val uri: String,
    val name: String,
    val description: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val text: String?,
    val blob: ByteArray?,
)

internal fun SentMessageAttachment.toRecord(): SentAttachmentRecord {
    val content = (this as? SentMessageAttachment.Resource)?.content
    return SentAttachmentRecord(
        mode = when (this) {
            is SentMessageAttachment.Resource -> "resource"
            is SentMessageAttachment.ResourceLink -> "resource_link"
        },
        uri = uri,
        name = metadata.name,
        description = metadata.description,
        mimeType = metadata.mimeType,
        sizeBytes = metadata.sizeBytes,
        text = (content as? SentMessageAttachment.Resource.Content.Text)?.value,
        blob = (content as? SentMessageAttachment.Resource.Content.Binary)?.let { Base64.decode(it.base64) },
    )
}

internal fun SentAttachmentRecord.toDomain(fileId: String): SentMessageAttachment {
    val metadata = SentAttachmentMetadata(fileId, name, description, mimeType, sizeBytes)
    return when (mode) {
        "resource" -> {
            val content = when {
                text != null && blob == null -> SentMessageAttachment.Resource.Content.Text(text)
                text == null && blob != null -> SentMessageAttachment.Resource.Content.Binary(Base64.encode(blob))
                else -> error("Invalid contents of a stored resource")
            }
            SentMessageAttachment.Resource(metadata, uri, content)
        }

        "resource_link" -> {
            check(text == null && blob == null)
            SentMessageAttachment.ResourceLink(metadata, uri)
        }

        else -> error("Unknown stored attachment transfer mode: $mode")
    }
}
