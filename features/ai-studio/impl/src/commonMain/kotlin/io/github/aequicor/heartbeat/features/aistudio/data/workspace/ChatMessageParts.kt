package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.data.dao.syncPart
import io.github.aequicor.heartbeat.features.aistudio.data.dao.writeAttachmentSnapshot
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.AgentArtifactPart
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Message_part
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

internal fun Message.header(): String = Json.encodeToString<Message>(
    when (this) {
        is User -> copy(text = "", files = listOf())
        is Reply -> copy(steps = listOf(), text = "", interim = null, result = null, artifacts = listOf())
    },
)

internal fun Message.revisionStatus(): String = when (this) {
    is User -> when (delivery) {
        Delivery.Sending -> "draft"
        Delivery.Sent -> "completed"
        Delivery.Failed -> "error"
    }
    is Reply -> when (status) {
        ReplyStatus.Working -> "draft"
        ReplyStatus.Completed -> "completed"
        ReplyStatus.Stopped -> "stopped"
        ReplyStatus.Failed -> "error"
    }
}

internal fun ChatQueries.writeParts(revision: Long, message: Message) {
    val remaining = ArrayDeque(selectParts(revision).executeAsList())
    var position = 0L
    fun part(
        kind: String, body: String? = null, parent: Long? = null, title: String? = null,
        status: String? = null, toolId: String? = null, snapshot: String? = null, metadata: String? = null,
    ): Long = syncPart(
        remaining,
        Message_part(0, revision, parent, position++, kind, title, status, body, toolId, metadata, snapshot),
    )

    fun filePart(file: File, metadata: String? = null, parent: Long? = null) =
        part("attachment", title = file.name, snapshot = storeAttachment(file), metadata = metadata, parent = parent)

    when (message) {
        is User -> {
            if (message.text.isNotEmpty()) part("text", message.text)
            message.files.forEach { filePart(it) }
        }
        is Reply -> {
            for (step in message.steps) {
                val header = step.copy(details = "", events = listOf(), result = null)
                val parent = part(
                    "step", step.details, title = step.title, status = step.status.name,
                    metadata = Json.encodeToString(header),
                )
                for (event in step.events) {
                    val kind = event.partKind()
                    val eventPart = part(
                        kind, event.details, parent, event.title,
                        event.status.name, toolId = event.id.takeIf { kind == "tool_call" },
                        metadata = Json.encodeToString(event.copy(details = "", files = emptyList())),
                    )
                    event.files.forEach { filePart(it, parent = eventPart) }
                }
                if (step.result != null) part("text", step.result, parent)
            }
            if (message.interim != null) part("text", message.interim, title = "interim")
            if (message.text.isNotEmpty()) part("text", message.text)
            message.result?.let { filePart(it) }
            message.artifacts.forEach { filePart(it, AgentArtifactPart) }
        }
    }
    if (remaining.isNotEmpty()) deletePartsFrom(revision, remaining.first().position)
}

private fun WorkEvent.partKind(): String = if (kind == WorkEventKind.Thinking) "reasoning" else "tool_call"

private fun ChatQueries.storeAttachment(file: File): String {
    if (file.storedAttachmentId != null) return file.storedAttachmentId
    val binary = file.base64Encoded || file.image != null
    val mime = file.mimeType ?: file.image?.mimeType ?: if (file.isMarkdown) "text/markdown" else "text/plain"
    val size = file.storedSizeBytes ?: if (binary) Base64.decode(file.content).size.toLong()
    else file.content.encodeToByteArray().size.toLong()
    return writeAttachmentSnapshot(
        ChatTurnMessageRecord.Attachment(
            null, file.name, mime, file.content, file.storedAttachmentHeader(), size,
            originalUri = file.originalUri,
            managedPath = file.managedCopyPath,
            base64Encoded = binary,
            description = file.description,
            linkUri = file.originalUri?.takeIf { file.deferredReading && file.managedCopyPath == null },
        ),
        prepared = null,
    )
}
