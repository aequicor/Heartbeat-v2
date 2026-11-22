package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord.Part
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
            }
        }
    }
    return ChatTurnMessageRecord(id, role, storedStatus(), header.toString(), parts())
}

private fun ChatHistoryEntry.Message.parts(): List<Part> = buildList {
    if (text.isNotEmpty()) add(Part("text", body = text))
    when (this@parts) {
        is ChatHistoryEntry.User -> files.forEach { add(it.toPart()) }
        is ChatHistoryEntry.Reply -> {
            interim?.let { add(Part("text", body = it, title = "interim")) }
            for (step in steps) {
                val parent = size
                add(Part("step", body = step.details, title = step.title, status = step.status.name, header = step.header()))
                for (event in step.events) add(event.toPart(parent))
                step.result?.let { add(Part("text", parent = parent, body = it)) }
            }
            result?.let { add(it.toPart()) }
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
        detailLabel?.let { put("detailLabel", it) }
        detailsFormat?.let { put("detailsFormat", it.name) }
    }
    return Part(
        if (reasoning) "reasoning" else "tool_call", body = details, parent = parent,
        title = title, status = status.name, toolId = id.takeUnless { reasoning }, header = header.toString(),
    )
}

private fun ChatAttachment.toPart(): Part {
    val header = buildJsonObject {
        put("id", id)
        put("name", name)
        put("kind", kind)
        put("content", "")
        date?.let { put("date", it.header()) }
    }
    return Part(
        "attachment", title = name,
        attachment = ChatTurnMessageRecord.Attachment(storageId, name, content, header.toString()),
    )
}

private fun ChatAttachment.Date.header() = buildJsonObject {
    put("year", year)
    put("month", month)
    put("day", day)
    put("time", time)
}

private const val MessageSerialPrefix = "io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState"
