package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Message_part
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import kotlinx.serialization.json.Json

internal fun ChatQueries.readMessage(revision: Long, header: String, headersOnly: Boolean = false): Message {
    val query = if (headersOnly) selectPreviewParts(revision, ::Message_part) else selectParts(revision)
    val parts = query.executeAsList().groupBy { it.parent_part_id }
    val roots = parts[null].orEmpty()
    fun text(title: String? = null) = roots.firstOrNull { it.kind == "text" && it.title == title }?.body
    val files = roots.filter { it.kind == "attachment" }.map {
        val id = checkNotNull(it.attachment_id)
        if (headersOnly) attachmentHeader(id, ::storedFileHeader).executeAsOne() else readFile(id)
    }
    return when (val message = Json.decodeFromString<Message>(header)) {
        is User -> message.copy(text = text().orEmpty(), files = files)
        is Reply -> message.copy(
            steps = roots.filter { it.kind == "step" }.map { readStep(it, parts[it.id].orEmpty(), headersOnly) },
            interim = text("interim"), text = text().orEmpty(), result = files.singleOrNull(),
        )
    }
}

internal fun storedFileHeader(id: String, size: Long, metadata: String?): File =
    Json.decodeFromString<File>(checkNotNull(metadata)).copy(storedAttachmentId = id, storedSizeBytes = size)

internal fun ChatQueries.readFile(id: String): File {
    val file = selectAttachment(id).executeAsOne()
    return Json.decodeFromString<File>(checkNotNull(file.presentation)).copy(content = checkNotNull(file.inline_text))
}

private fun readStep(part: Message_part, children: List<Message_part>, headersOnly: Boolean): Step =
    Json.decodeFromString<Step>(checkNotNull(part.presentation)).copy(
        details = part.body.orEmpty(),
        events = children.filter { it.kind == "reasoning" || it.kind == "tool_call" }.map { child ->
            Json.decodeFromString<WorkEvent>(checkNotNull(child.presentation)).copy(
                details = child.body.orEmpty(), storedBodyId = child.id.takeIf { headersOnly },
            )
        },
        result = children.firstOrNull { it.kind == "text" }?.body,
    )
