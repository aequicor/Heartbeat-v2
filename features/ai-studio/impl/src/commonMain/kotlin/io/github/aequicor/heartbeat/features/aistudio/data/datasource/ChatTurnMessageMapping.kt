package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord.Part
import io.github.aequicor.heartbeat.features.aistudio.data.model.toRecord
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentTransferMode
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.isMarkdownAttachment
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

internal fun ChatHistoryEntry.Message.toTurnRecord(): ChatTurnMessageRecord {
    val role = if (this is ChatHistoryEntry.User) "user" else "assistant"
    val header = buildJsonObject {
        put("type", "$MessageSerialPrefix.${if (this@toTurnRecord is ChatHistoryEntry.User) "User" else "Reply"}")
        put("id", id)
        put("time", time)
        promptId?.let { put("promptId", it) }
        when (this@toTurnRecord) {
            is ChatHistoryEntry.User -> {
                put("text", "")
                put("delivery", delivery.name)
                error?.let { put("error", it) }
                put("answers", answers)
                put("scenario", scenario.name)
                put("access", access.name)
                put("retryAllowed", retryAllowed)
            }
            is ChatHistoryEntry.Reply -> {
                put("status", status.name)
                put("retryAllowed", retryAllowed)
                failure?.let { put("failure", it.name) }
            }
        }
    }
    val revisionId = (this as? ChatHistoryEntry.User)?.revisionId
    return ChatTurnMessageRecord(
        id, role, storedStatus(), header.toString(), if (revisionId == null) parts() else emptyList(),
        expectedRevisionId = revisionId,
    )
}

private fun ChatHistoryEntry.Message.parts(): List<Part> = buildList {
    if (text.isNotEmpty()) add(Part("text", body = text))
    when (this@parts) {
        is ChatHistoryEntry.User -> {
            check(sentAttachments.isEmpty() || sentAttachments.size == files.size)
            files.forEachIndexed { index, file ->
                val snapshot = sentAttachments.getOrNull(index)
                check(snapshot == null || snapshot.metadata.fileId == file.id)
                add(file.toPart().copy(snapshot = snapshot?.toRecord()))
            }
        }
        is ChatHistoryEntry.Reply -> {
            interim?.let { add(Part("text", body = it, title = "interim")) }
            for (step in steps) {
                val parent = size
                add(Part("step", body = step.details, title = step.title, status = step.status.name, header = step.header()))
                for (event in step.events) {
                    val eventPosition = size
                    add(event.toPart(parent))
                    event.files.forEach { add(it.toPart(parent = eventPosition)) }
                }
                step.result?.let { add(Part("text", parent = parent, body = it)) }
            }
            result?.let { add(it.toPart()) }
            artifacts.forEach { add(it.toPart(AgentArtifactPart)) }
        }
    }
}

private fun ChatHistoryEntry.Message.storedStatus(): String = when (this) {
    is ChatHistoryEntry.User -> when (delivery) {
        ChatHistoryEntry.Delivery.Sending -> "draft"
        ChatHistoryEntry.Delivery.Sent -> "completed"
        ChatHistoryEntry.Delivery.Failed -> "error"
    }
    is ChatHistoryEntry.Reply -> when (status) {
        ChatHistoryEntry.ReplyStatus.Working -> "draft"
        ChatHistoryEntry.ReplyStatus.Completed -> "completed"
        ChatHistoryEntry.ReplyStatus.Stopped -> "stopped"
        ChatHistoryEntry.ReplyStatus.Failed -> "error"
    }
}

private fun ChatHistoryEntry.Step.header() = buildJsonObject {
    put("id", id)
    put("title", title)
    put("status", status.name)
    put("details", "")
    put("activeTitle", activeTitle)
}.toString()

private fun ChatHistoryEntry.Event.toPart(parent: Int): Part {
    val reasoning = kind == ChatHistoryEntry.EventKind.Thinking
    val header = buildJsonObject {
        put("id", id)
        put("title", title)
        put("summary", summary)
        put("details", "")
        put("kind", kind.name)
        put("status", status.name)
        operation?.let { put("operation", it.name) }
        command?.let { put("command", it) }
        detailLabel?.let { put("detailLabel", it) }
        detailsFormat?.let { put("detailsFormat", it.name) }
    }
    return Part(
        if (reasoning) "reasoning" else "tool_call", body = details, parent = parent,
        title = title, status = status.name, toolId = id.takeUnless { reasoning }, header = header.toString(),
    )
}

private fun ChatAttachment.toPart(partHeader: String? = null, parent: Int? = null): Part {
    val header = buildJsonObject {
        put("id", id)
        put("name", name)
        put("kind", kind)
        put("content", "")
        source?.let {
            it.description?.let { description -> put("description", description) }
            it.sizeBytes?.let { size -> put("sourceSizeBytes", size) }
        }
        mimeType?.let { put("mimeType", it) }
        if (base64Encoded) put("base64Encoded", true)
        date?.let { put("date", it.header()) }
        image?.let { image ->
            put("image", buildJsonObject {
                put("mimeType", image.mimeType)
                image.thumbnail?.let { put("thumbnail", it) }
            })
        }
    }
    val mimeType = mimeType ?: image?.mimeType ?: if (isMarkdownAttachment(name, kind)) "text/markdown" else "text/plain"
    val size = size ?: if (image != null || base64Encoded) Base64.decode(content).size.toLong()
    else content.encodeToByteArray().size.toLong()
    return Part(
        "attachment", title = name, header = partHeader, parent = parent,
        attachment = ChatTurnMessageRecord.Attachment(
            storageId, name, mimeType, content, header.toString(), size,
            originalUri = source?.location?.originalUri,
            managedPath = (source?.location as? AttachmentLocation.ManagedCopy)?.relativePath,
            base64Encoded = base64Encoded || image != null,
            description = source?.description,
            linkUri = (source?.location as? AttachmentLocation.Original)?.reference
                ?.takeIf { transferMode == AttachmentTransferMode.ResourceLink },
        ),
    )
}

private fun ChatAttachment.Date.header() = buildJsonObject {
    put("year", year)
    put("month", month)
    put("day", day)
    put("time", time)
}

private const val MessageSerialPrefix = "io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState"
internal const val AgentArtifactPart = """{"role":"agent_artifact"}"""
