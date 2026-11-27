package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aistudio.data.dao.reasoningPreview
import io.github.aequicor.heartbeat.features.aistudio.data.dto.ChatMessageBodyPart
import io.github.aequicor.heartbeat.features.aistudio.data.dto.decodeChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.toDomain

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentTransferMode
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException

internal fun ChatEntryRecord.toDomain(keepAttachmentReference: Boolean = false): ChatHistoryEntry = decodeHistory {
    val meta = Json.parseToJsonElement(header).jsonObject
    val id = meta.string("id") ?: error("Missing message ID")
    val time = meta.getValue("time").jsonPrimitive.content
    val children = parts.groupBy { it.parent }
    val roots = children[null].orEmpty()
    fun text(title: String? = null) = roots.firstOrNull { it.kind == "text" && it.title == title }?.body
    val attachments = roots.mapNotNull { part ->
        part.attachment?.toDomain(keepAttachmentReference)?.let { part.header to it }
    }
    val prompt = meta.string("promptId")
    val content = when (role) {
        "user" -> ChatHistoryEntry.User(
            id, time, text().orEmpty(), prompt, attachments.map { it.second },
            meta.enumOrNull<ChatHistoryEntry.Delivery>("delivery") ?: ChatHistoryEntry.Delivery.Sent,
            meta.string("error"), meta["answers"]?.jsonPrimitive?.boolean ?: false,
            meta.enumOrNull<ChatHistoryEntry.Scenario>("scenario") ?: ChatHistoryEntry.Scenario.Standard,
            meta.enumOrNull<ChatHistoryEntry.Access>("access") ?: ChatHistoryEntry.Access.Workspace,
            retryAllowed = meta["retryAllowed"]?.jsonPrimitive?.boolean ?: false,
            revisionId = revision,
            sentAttachments = roots.toDeliverySnapshots(),
            messageBody = decodeChatMessageBody(text(ChatMessageBodyPart)),
        )
        "assistant" -> {
            val status = meta.enumOrNull<ChatHistoryEntry.ReplyStatus>("status") ?: ChatHistoryEntry.ReplyStatus.Working
            val steps = roots.filter { it.kind == "step" }.map { it.toStep(children, keepAttachmentReference) }
            val failure = (meta["failure"] as? JsonPrimitive)?.contentOrNull
            val (artifacts, legacy) = attachments.partition { it.first == AgentArtifactPart }
            ChatHistoryEntry.Reply(
                id, time, text().orEmpty(), prompt, text("interim"), status,
                steps.groupLegacyWork(status == ChatHistoryEntry.ReplyStatus.Working),
                legacy.singleOrNull()?.second,
                retryAllowed = meta["retryAllowed"]?.jsonPrimitive?.boolean ?: true,
                failure = ChatHistoryEntry.ReplyFailure.entries.firstOrNull { it.name == failure },
                artifacts = artifacts.map { it.second },
            )
        }
        else -> error("Unknown message role: $role")
    }
    ChatHistoryEntry(position, revision, message, predatesEdit, content)
}

private fun List<ChatEntryRecord.Part>.toDeliverySnapshots() =
    if (none { it.snapshot != null }) emptyList()
    else filter { it.kind == "attachment" }.map { part ->
        val file = checkNotNull(part.attachment).toDomain()
        checkNotNull(part.snapshot) { "Missing snapshot at message part ${part.id}" }.toDomain(file.id)
    }

private fun List<ChatHistoryEntry.Step>.groupLegacyWork(working: Boolean): List<ChatHistoryEntry.Step> {
    if (none { it.isLegacyCall }) return this
    return buildList {
        val pending = mutableListOf<ChatHistoryEntry.Step>()
        fun flush(following: ChatHistoryEntry.Step? = null, trailing: Boolean = false) {
            val first = pending.firstOrNull() ?: return
            val last = following ?: pending.last()
            val events = pending.map { step ->
                val event = step.events.single()
                if (event.kind == ChatHistoryEntry.EventKind.Thinking) {
                    event.copy(operation = ChatHistoryEntry.ToolOperation.THINK)
                } else event
            } + following?.events.orEmpty()
            val legacyGroup = following == null && events.size > 1
            val title = if (legacyGroup) {
                if (events.all { it.kind == ChatHistoryEntry.EventKind.Thinking }) "Размышления" else "Работа по запросу"
            } else last.title
            val activeTitle = if (legacyGroup) title else last.activeTitle
            val status = if (trailing && working && pending.any { it.status == ChatHistoryEntry.StepStatus.Current }) {
                ChatHistoryEntry.StepStatus.Current
            } else last.status
            add(last.copy(id = first.id, title = title, activeTitle = activeTitle, events = events, status = status))
            pending.clear()
        }
        for (step in this@groupLegacyWork) {
            if (step.isLegacyCall) {
                pending.add(step)
                if (step.result != null) flush()
            } else if (pending.isNotEmpty() && pending.all { it.events.single().kind == ChatHistoryEntry.EventKind.Thinking } &&
                step.events.any { it.kind != ChatHistoryEntry.EventKind.Thinking }
            ) {
                flush(step)
            } else {
                flush()
                add(step)
            }
        }
        flush(trailing = true)
    }
}

// Older ACP stored an individual step with the same ID and title as every call.
private val ChatHistoryEntry.Step.isLegacyCall: Boolean
    get() = events.singleOrNull()?.let { event ->
        details.isBlank() && event.id == id && event.operation == null && activeTitle == title &&
            event.detailLabel == null && matchesLegacyLabel(event)
    } == true

private fun ChatHistoryEntry.Step.matchesLegacyLabel(event: ChatHistoryEntry.Event): Boolean {
    val stopped = event.status == ChatHistoryEntry.StepStatus.Stopped
    return when (event.kind) {
        ChatHistoryEntry.EventKind.Thinking -> title == "Размышления" &&
            (event.title == title || stopped && event.title == "Размышления прерваны")
        ChatHistoryEntry.EventKind.Check -> event.title == title &&
            event.detailsFormat == ChatHistoryEntry.DetailFormat.Plain &&
            (event.summary == title || stopped && event.summary == "Остановлено")
        else -> false
    }
}

private fun ChatEntryRecord.Part.toStep(
    partsByParent: Map<Long?, List<ChatEntryRecord.Part>>,
    keepAttachmentReference: Boolean,
): ChatHistoryEntry.Step {
    val children = partsByParent[id].orEmpty()
    val meta = Json.parseToJsonElement(checkNotNull(header)).jsonObject
    val title = meta.getValue("title").jsonPrimitive.content
    return ChatHistoryEntry.Step(
        meta.getValue("id").jsonPrimitive.content, title, body.orEmpty(),
        enumValueOf(meta.getValue("status").jsonPrimitive.content),
        children.firstOrNull { it.kind == "text" }?.body, meta.string("activeTitle") ?: title,
        children.filter { it.kind == "reasoning" || it.kind == "tool_call" }.map { event ->
            val files = partsByParent[event.id].orEmpty()
                .mapNotNull { it.attachment?.toDomain(keepAttachmentReference) }
            event.toEvent(files)
        },
    )
}

private fun ChatEntryRecord.Part.toEvent(files: List<ChatAttachment>): ChatHistoryEntry.Event {
    val meta = Json.parseToJsonElement(checkNotNull(header)).jsonObject
    val summary = meta.getValue("summary").jsonPrimitive.content.ifBlank {
        if (kind == "reasoning") body.orEmpty().reasoningPreview() else ""
    }
    return ChatHistoryEntry.Event(
        meta.getValue("id").jsonPrimitive.content, meta.string("title"),
        summary, body.takeUnless { deferred }.orEmpty(),
        enumValueOf(meta.getValue("kind").jsonPrimitive.content),
        enumValueOf(meta.getValue("status").jsonPrimitive.content),
        meta.string("detailLabel"), meta.enumOrNull<ChatHistoryEntry.DetailFormat>("detailsFormat"), id.takeIf { deferred },
        meta.enumOrNull<ChatHistoryEntry.ToolOperation>("operation"),
        meta.string("command"),
        files,
    )
}

internal fun ChatEntryRecord.Attachment.toDomain(keepReference: Boolean = false): ChatAttachment = decodeHistory {
    val meta = Json.parseToJsonElement(checkNotNull(header)).jsonObject
    val date = (meta["date"] as? JsonObject)?.let {
        ChatAttachment.Date(
            it.getValue("year").jsonPrimitive.int, it.getValue("month").jsonPrimitive.int,
            it.getValue("day").jsonPrimitive.int, it.getValue("time").jsonPrimitive.content,
        )
    }
    val hasReference = content == null || keepReference
    val imageMeta = meta["image"] as? JsonObject
    val base64Encoded = binarySnapshot || (meta["base64Encoded"] as? JsonPrimitive)?.boolean == true
    val mediaType = mimeType ?: (imageMeta?.get("mimeType") as? JsonPrimitive)?.contentOrNull
        ?: (meta["mimeType"] as? JsonPrimitive)?.contentOrNull
    val format = ChatImage.forMimeType(mediaType)
    val image = if (format != null && imageMeta != null) {
        ChatAttachment.Image(format.mimeType, (imageMeta["thumbnail"] as? JsonPrimitive)?.contentOrNull)
    } else null
    val fileId = meta.getValue("id").jsonPrimitive.content
    val name = meta.getValue("name").jsonPrimitive.content
    val location = managedPath?.let { AttachmentLocation.ManagedCopy(it, originalUri) }
        ?: originalUri?.let { AttachmentLocation.Original(it) }
    ChatAttachment(
        fileId, name,
        meta.getValue("kind").jsonPrimitive.content, content.orEmpty(),
        date ?: meta.string("editedAt")?.toAttachmentDate() ?: addedAt?.toAttachmentDate(),
        id.takeIf { hasReference }, size.takeIf { hasReference || image != null }, image,
        mimeType = mediaType,
        base64Encoded = base64Encoded,
        source = location?.let {
            AttachmentFile(fileId, name, it, meta.string("description"),
                mediaType, meta["sourceSizeBytes"]?.jsonPrimitive?.longOrNull)
        },
        transferMode = if (transferMode == "resource_link") {
            AttachmentTransferMode.ResourceLink
        } else AttachmentTransferMode.Resource,
    )
}

private fun String.toAttachmentDate(): ChatAttachment.Date? {
    if (length != 16) return null
    return ChatAttachment.Date(
        substring(0, 4).toInt(), substring(5, 7).toInt(), substring(8, 10).toInt(), substring(11),
    )
}

private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull

private inline fun <reified T : Enum<T>> JsonObject.enumOrNull(key: String): T? = string(key)?.let { enumValueOf<T>(it) }

@Suppress("TooGenericExceptionCaught") // Malformed stored headers can fail during JSON, field, or enum decoding.
private inline fun <T> decodeHistory(read: () -> T): T = try {
    read()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: StudioRepositoryException) {
    throw failure
} catch (failure: Exception) {
    throw StudioRepositoryException("Invalid stored chat history", failure)
}
