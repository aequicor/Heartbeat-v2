package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatInterruptionLabels
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.uuid.Uuid

internal fun String.withDraftOwner(owner: Uuid): String =
    JsonObject(header() + (DraftOwnerKey to JsonPrimitive(owner.toString()))).toString()

internal fun String.draftOwner(): Uuid? = header().draftOwner()

internal fun ChatQueries.recoverInterruptedDrafts(chatId: String, ownership: ChatDraftOwnership, labels: ChatInterruptionLabels) {
    val ownersEnded = mutableMapOf<Uuid, Boolean>()
    val drafts = selectChatDrafts(chatId).executeAsList().mapNotNull { revision ->
        val header = revision.presentation.header()
        val owner = header.draftOwner()
        if (owner == null || !ownersEnded.getOrPut(owner) { ownership.ended(owner) }) return@mapNotNull null
        revision to header
    }
    if (drafts.isEmpty()) return
    transaction {
        for ((revision, header) in drafts) {
            val user = revision.role == "user"
            val terminal = if (user) {
                mapOf(
                    "delivery" to JsonPrimitive("Failed"), "error" to JsonPrimitive(labels.sendError),
                    "retryAllowed" to JsonPrimitive(false),
                )
            } else {
                mapOf("status" to JsonPrimitive("Stopped"))
            }
            val updated = recoverDraft(
                presentation = JsonObject(header - DraftOwnerKey + terminal).toString(),
                status = if (user) "error" else "stopped",
                revision_id = revision.revision_id,
                draft_presentation = revision.presentation,
            ).value
            if (updated == 1L && !user) stopInterruptedParts(revision.revision_id, labels)
        }
    }
}

private fun ChatQueries.stopInterruptedParts(revisionId: Long, labels: ChatInterruptionLabels) {
    for (part in selectActiveParts(revisionId).executeAsList()) {
        val title = labels.reasoningTitle.takeIf { part.kind == "reasoning" }
        val header = part.presentation?.header()?.toMutableMap()?.apply {
            put("status", JsonPrimitive("Stopped"))
            if (title != null) put("title", JsonPrimitive(title))
            if (part.kind == "tool_call") put("summary", JsonPrimitive(labels.toolSummary))
        }
        stopActivePart(title, header?.let(::JsonObject)?.toString(), part.id)
    }
}

private fun String.header(): JsonObject = Json.parseToJsonElement(this) as JsonObject

private fun JsonObject.draftOwner(): Uuid? =
    (get(DraftOwnerKey) as? JsonPrimitive)?.contentOrNull?.let(Uuid::parseOrNull)

private const val DraftOwnerKey = "draftOwner"
