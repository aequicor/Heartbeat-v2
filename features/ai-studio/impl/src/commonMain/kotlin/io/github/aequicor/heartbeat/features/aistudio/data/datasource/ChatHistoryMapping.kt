package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

internal fun ChatEntryRecord.toDomain(): ChatHistoryEntry = decodeHistory {
    val meta = Json.parseToJsonElement(header).jsonObject
    val id = meta.string("id") ?: error("Missing message ID")
    val time = meta.getValue("time").jsonPrimitive.content
    val children = parts.groupBy { it.parent }
    val roots = children[null].orEmpty()
    fun text(title: String? = null) = roots.firstOrNull { it.kind == "text" && it.title == title }?.body
    val files = roots.mapNotNull { it.attachment?.toDomain() }
    val prompt = meta.string("promptId")
    val content = when (role) {
        "user" -> ChatHistoryEntry.User(
            id, time, text().orEmpty(), prompt, files,
            meta.enumOrNull<ChatHistoryEntry.Delivery>("delivery") ?: ChatHistoryEntry.Delivery.Sent,
            meta.string("error"), meta["answers"]?.jsonPrimitive?.boolean ?: false,
            meta.enumOrNull<ChatHistoryEntry.Scenario>("scenario") ?: ChatHistoryEntry.Scenario.Standard,
            meta.enumOrNull<ChatHistoryEntry.Access>("access") ?: ChatHistoryEntry.Access.Workspace,
            retryAllowed = meta["retryAllowed"]?.jsonPrimitive?.boolean ?: true,
        )
        "assistant" -> ChatHistoryEntry.Reply(
            id, time, text().orEmpty(), prompt, text("interim"),
            meta.enumOrNull<ChatHistoryEntry.ReplyStatus>("status") ?: ChatHistoryEntry.ReplyStatus.Working,
            roots.filter { it.kind == "step" }.map { it.toStep(children[it.id].orEmpty()) },
            files.singleOrNull(),
            retryAllowed = meta["retryAllowed"]?.jsonPrimitive?.boolean ?: true,
        )
        else -> error("Unknown message role: $role")
    }
    ChatHistoryEntry(position, revision, message, predatesEdit, content)
}

private fun ChatEntryRecord.Part.toStep(children: List<ChatEntryRecord.Part>): ChatHistoryEntry.Step {
    val meta = Json.parseToJsonElement(checkNotNull(header)).jsonObject
    val title = meta.getValue("title").jsonPrimitive.content
    return ChatHistoryEntry.Step(
        meta.getValue("id").jsonPrimitive.content, title, body.orEmpty(),
        enumValueOf(meta.getValue("status").jsonPrimitive.content),
        children.firstOrNull { it.kind == "text" }?.body, meta.string("activeTitle") ?: title,
        children.filter { it.kind == "reasoning" || it.kind == "tool_call" }.map { it.toEvent() },
    )
}

private fun ChatEntryRecord.Part.toEvent(): ChatHistoryEntry.Event {
    val meta = Json.parseToJsonElement(checkNotNull(header)).jsonObject
    return ChatHistoryEntry.Event(
        meta.getValue("id").jsonPrimitive.content, meta.string("title"),
        meta.getValue("summary").jsonPrimitive.content, body.orEmpty(),
        enumValueOf(meta.getValue("kind").jsonPrimitive.content),
        enumValueOf(meta.getValue("status").jsonPrimitive.content),
        meta.string("detailLabel"), meta.enumOrNull<ChatHistoryEntry.DetailFormat>("detailsFormat"), id.takeIf { deferred },
    )
}

internal fun ChatEntryRecord.Attachment.toDomain(): ChatAttachment = decodeHistory {
    val meta = Json.parseToJsonElement(checkNotNull(header)).jsonObject
    val date = (meta["date"] as? JsonObject)?.let {
        ChatAttachment.Date(
            it.getValue("year").jsonPrimitive.int, it.getValue("month").jsonPrimitive.int,
            it.getValue("day").jsonPrimitive.int, it.getValue("time").jsonPrimitive.content,
        )
    }
    ChatAttachment(
        meta.getValue("id").jsonPrimitive.content, meta.getValue("name").jsonPrimitive.content,
        meta.getValue("kind").jsonPrimitive.content, content.orEmpty(), date,
        id.takeIf { content == null }, size.takeIf { content == null },
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
