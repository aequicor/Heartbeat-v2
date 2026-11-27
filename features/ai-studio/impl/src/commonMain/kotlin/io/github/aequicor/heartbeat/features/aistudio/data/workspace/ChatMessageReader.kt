package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.data.dao.fileHeader
import io.github.aequicor.heartbeat.features.aistudio.data.dao.fullAttachment
import io.github.aequicor.heartbeat.features.aistudio.data.dao.reasoningPreview
import io.github.aequicor.heartbeat.features.aistudio.data.dao.selectHistoryParts
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.AgentArtifactPart
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
    val query = selectHistoryParts(revision, headersOnly)
    val parts = query.executeAsList().groupBy { it.parent_part_id }
    val roots = parts[null].orEmpty()
    fun text(title: String? = null) = roots.firstOrNull { it.kind == "text" && it.title == title }?.body
    val files = roots.filter { it.kind == "attachment" }.map { part ->
        part.presentation to readAttachmentPart(part, headersOnly)
    }
    return when (val message = Json.decodeFromString<Message>(header)) {
        is User -> message.copy(text = text().orEmpty(), files = files.map { it.second })
        is Reply -> {
            val (artifacts, legacy) = files.partition { it.first == AgentArtifactPart }
            message.copy(
                steps = roots.filter { it.kind == "step" }.map { readStep(it, parts, headersOnly) },
                interim = text("interim"), text = text().orEmpty(),
                result = legacy.singleOrNull()?.second, artifacts = artifacts.map { it.second },
            )
        }
    }
}

private fun ChatQueries.readAttachmentPart(part: Message_part, headersOnly: Boolean): File {
    val id = checkNotNull(part.sent_attachment_id)
    val snapshot = if (headersOnly) fileHeader(id) else fullAttachment(id)
    val file = Json.decodeFromString<File>(checkNotNull(snapshot.header))
    return file.copy(
        content = snapshot.content.orEmpty(),
        storedAttachmentId = id.takeIf { snapshot.content == null },
        storedSizeBytes = snapshot.size.takeIf { snapshot.content == null },
        base64Encoded = snapshot.binarySnapshot || file.base64Encoded,
        deferredReading = snapshot.transferMode == "resource_link",
        originalUri = snapshot.originalUri,
        managedCopyPath = snapshot.managedPath,
    )
}

private fun ChatQueries.readStep(part: Message_part, parts: Map<Long?, List<Message_part>>, headersOnly: Boolean): Step {
    val children = parts[part.id].orEmpty()
    return Json.decodeFromString<Step>(checkNotNull(part.presentation)).copy(
        details = part.body.orEmpty(),
        events = children.filter { it.kind == "reasoning" || it.kind == "tool_call" }.map { child ->
            val event = Json.decodeFromString<WorkEvent>(checkNotNull(child.presentation))
            val summary = event.summary.ifBlank {
                if (child.kind == "reasoning") child.body.orEmpty().reasoningPreview() else ""
            }
            event.copy(
                summary = summary,
                details = child.body.takeUnless { headersOnly }.orEmpty(),
                storedBodyId = child.id.takeIf { headersOnly },
                files = parts[child.id].orEmpty().filter { it.kind == "attachment" }
                    .map { readAttachmentPart(it, headersOnly) },
            )
        },
        result = children.firstOrNull { it.kind == "text" }?.body,
    )
}
