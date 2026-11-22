package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.data.dao.syncPart
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
import kotlin.uuid.Uuid

internal fun Message.header(): String = Json.encodeToString<Message>(
    when (this) {
        is User -> copy(text = "", files = listOf())
        is Reply -> copy(steps = listOf(), text = "", interim = null, result = null)
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
        status: String? = null, toolId: String? = null, attachment: String? = null, metadata: String? = null,
    ): Long = syncPart(
        remaining,
        Message_part(0, revision, parent, position++, kind, title, status, body, toolId, attachment, metadata),
    )

    fun filePart(file: File) = part("attachment", title = file.name, attachment = storeAttachment(file))

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
                    part(
                        kind, event.details, parent, event.title,
                        event.status.name, toolId = event.id.takeIf { kind == "tool_call" },
                        metadata = Json.encodeToString(event.copy(details = "")),
                    )
                }
                if (step.result != null) part("text", step.result, parent)
            }
            if (message.interim != null) part("text", message.interim, title = "interim")
            if (message.text.isNotEmpty()) part("text", message.text)
            if (message.result != null) filePart(message.result)
        }
    }
    if (remaining.isNotEmpty()) deletePartsFrom(revision, remaining.first().position)
}

private fun WorkEvent.partKind(): String = if (kind == WorkEventKind.Thinking) "reasoning" else "tool_call"

// Aurora's existing file picker supplies embedded text. Keep those bytes durable and share
// an attachment only when both its metadata and content match the earlier version.
private fun ChatQueries.storeAttachment(file: File): String {
    if (file.storedAttachmentId != null) return file.storedAttachmentId
    val metadata = Json.encodeToString(file.copy(content = ""))
    return findInlineAttachment(metadata, file.content).executeAsOneOrNull() ?: insertFile(file, metadata)
}

private fun ChatQueries.insertFile(file: File, metadata: String): String {
    val id = Uuid.random().toString()
    val mime = if (file.isMarkdown) "text/markdown" else "text/plain"
    insertInlineAttachment(
        id, file.name, mime, file.content.encodeToByteArray().size.toLong(),
        "sqlite:attachment/$id", metadata, file.content,
    )
    return id
}
