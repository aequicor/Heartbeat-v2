package io.github.aequicor.heartbeat.features.aistudio.data.dto

import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentTransferMode
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceReference
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceSnapshot
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SentAttachmentMetadata
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SentMessageAttachment.Resource.Content
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ChatMessageBodyDto(
    val reply: Snapshot? = null,
    val content: List<Node>,
) {

    @Serializable
    sealed interface Node {

        @Serializable
        @SerialName("text")
        data class Text(val value: String) : Node

        @Serializable
        @SerialName("quote")
        data class Quote(val snapshot: Snapshot) : Node
    }

    @Serializable
    data class Snapshot(
        val source: Reference,
        val role: String,
        val agentName: String?,
        val fragments: List<Fragment>,
        val attachments: List<Attachment> = emptyList(),
    )

    @Serializable
    data class Reference(
        val messageId: Long,
        val revisionId: Long,
        val kind: String,
        val blockId: String?,
        val toolCallId: String?,
        val resourceVersionId: String?,
        val range: Range?,
    )

    @Serializable
    data class Range(val start: Int, val endExclusive: Int)

    @Serializable
    data class Fragment(val text: String, val format: String, val formattedMarkdown: String? = null)

    @Serializable
    data class Attachment(
        val versionId: String,
        val fileId: String,
        val name: String,
        val description: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val transferMode: String,
        val uri: String?,
        val content: AttachmentContent?,
        val thumbnail: String?,
    )

    @Serializable
    sealed interface AttachmentContent {

        @Serializable
        @SerialName("text")
        data class Text(val value: String) : AttachmentContent

        @Serializable
        @SerialName("binary")
        data class Binary(val base64: String) : AttachmentContent
    }
}

internal fun ChatMessageBodyDto.toDomain(): ChatMessageBody = ChatMessageBody(
    reply = reply?.toDomain(),
    content = content.map { node ->
        when (node) {
            is ChatMessageBodyDto.Node.Text -> ChatMessageBody.Node.Text(node.value)
            is ChatMessageBodyDto.Node.Quote -> ChatMessageBody.Node.Quote(node.snapshot.toDomain())
        }
    },
)

internal fun ChatMessageBody.toDto(): ChatMessageBodyDto = ChatMessageBodyDto(
    reply = reply?.toDto(),
    content = content.map { node ->
        when (node) {
            is ChatMessageBody.Node.Text -> ChatMessageBodyDto.Node.Text(node.value)
            is ChatMessageBody.Node.Quote -> ChatMessageBodyDto.Node.Quote(node.snapshot.toDto())
        }
    },
)

internal const val ChatMessageBodyPart = "quote-reply"

internal fun encodeChatMessageBody(body: ChatMessageBody?): String? =
    body?.takeIf { it.reply != null || it.hasQuotes }?.let { Json.encodeToString(it.toDto()) }

internal fun decodeChatMessageBody(encoded: String?): ChatMessageBody? =
    encoded?.let { Json.decodeFromString<ChatMessageBodyDto>(it).toDomain() }

private fun ChatMessageBodyDto.Snapshot.toDomain() = ChatSourceSnapshot(
    source = ChatSourceReference(
        source.messageId, source.revisionId, ChatSourceReference.Kind.valueOf(source.kind), source.blockId,
        source.toolCallId, source.resourceVersionId,
        source.range?.let { ChatSourceReference.Range(it.start, it.endExclusive) },
    ),
    role = ChatSourceSnapshot.Role.valueOf(role),
    agentName = agentName,
    fragments = fragments.map {
        ChatSourceSnapshot.Fragment(it.text, ChatSourceSnapshot.Fragment.Format.valueOf(it.format), it.formattedMarkdown)
    },
    attachments = attachments.map { it.toDomain() },
)

private fun ChatSourceSnapshot.toDto() = ChatMessageBodyDto.Snapshot(
    source = ChatMessageBodyDto.Reference(
        source.messageId, source.revisionId, source.kind.name, source.blockId, source.toolCallId, source.resourceVersionId,
        source.range?.let { ChatMessageBodyDto.Range(it.start, it.endExclusive) },
    ),
    role = role.name,
    agentName = agentName,
    fragments = fragments.map { ChatMessageBodyDto.Fragment(it.text, it.format.name, it.formattedMarkdown) },
    attachments = attachments.map { it.toDto() },
)

private fun ChatMessageBodyDto.Attachment.toDomain() = ChatSourceSnapshot.Attachment(
    versionId, SentAttachmentMetadata(fileId, name, description, mimeType, sizeBytes), AttachmentTransferMode.valueOf(transferMode),
    uri,
    when (val payload = content) {
        is ChatMessageBodyDto.AttachmentContent.Text -> Content.Text(payload.value)
        is ChatMessageBodyDto.AttachmentContent.Binary -> Content.Binary(payload.base64)
        null -> null
    },
    thumbnail,
)

private fun ChatSourceSnapshot.Attachment.toDto() = ChatMessageBodyDto.Attachment(
    versionId, metadata.fileId, metadata.name, metadata.description, metadata.mimeType, metadata.sizeBytes,
    transferMode.name, uri,
    when (val payload = content) {
        is Content.Text -> ChatMessageBodyDto.AttachmentContent.Text(payload.value)
        is Content.Binary -> ChatMessageBodyDto.AttachmentContent.Binary(payload.base64)
        null -> null
    },
    thumbnail,
)
