package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.db.ChatDraftQueries
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal fun ChatDraftQueries.replaceDraft(
    history: ChatQueries,
    sessionId: String,
    revision: String,
    text: String,
    files: List<File>,
) {
    insertDraft(sessionId, revision, text)
    updateDraft(revision, text, sessionId)
    val previous = selectDraftFiles(sessionId).executeAsList()
    if (previous.map { decodeDraftFile(it.presentation, it.transfer_mode, it.original_uri, it.managed_path) } == files) return

    deleteDraftFiles(sessionId)
    files.forEachIndexed { index, file ->
        check(file.originalUri != null || file.managedCopyPath != null) {
            "Draft attachment ${file.id} has no file location"
        }
        val attachmentId = Uuid.random().toString()
        history.insertAttachment(
            attachmentId, file.name, file.mimeType ?: file.image?.mimeType, file.sourceSizeBytes ?: 0L,
            file.originalUri, file.managedCopyPath, file.storedAttachmentHeader(),
        )
        val mode = if (file.deferredReading) "resource_link" else "resource"
        insertDraftFile(sessionId, index.toLong(), file.id, attachmentId, mode)
    }
    if (previous.isNotEmpty()) history.deleteUnusedDraftFiles(previous.map { it.attachment_id })
}

internal fun ChatDraftQueries.readDraftFiles(sessionId: String): List<File> =
    selectDraftFiles(sessionId).executeAsList().map {
        decodeDraftFile(it.presentation, it.transfer_mode, it.original_uri, it.managed_path)
    }

internal fun File.storedAttachmentHeader(): String =
    Json.encodeToString(copy(content = "", deferredReading = false, originalUri = null, managedCopyPath = null))

private fun decodeDraftFile(presentation: String?, mode: String, original: String?, managed: String?): File =
    Json.decodeFromString<File>(checkNotNull(presentation)).copy(
        deferredReading = mode == "resource_link", originalUri = original, managedCopyPath = managed,
    )
