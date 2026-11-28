package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.data.dto.encodeChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.db.ChatDraftQueries
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.SelectDraftFiles
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal fun ChatDraftQueries.replaceDraft(
    history: ChatQueries,
    sessionId: String,
    revision: String,
    text: String,
    files: List<File>,
    body: ChatMessageBody? = null,
) {
    insertDraft(sessionId, revision, text)
    updateDraft(revision, text, encodeChatMessageBody(body), sessionId)
    val previous = selectDraftFiles(sessionId).executeAsList()
    if (previous.map(SelectDraftFiles::toFile) == files) return

    deleteDraftFiles(sessionId)
    files.forEachIndexed { index, file ->
        val snapshotId = file.catalogSnapshotId
        val mode = if (file.deferredReading) "resource_link" else "resource"
        if (snapshotId != null) {
            check(insertCatalogDraftFile(sessionId, index.toLong(), file.id, mode, snapshotId).value == 1L)
            return@forEachIndexed
        }
        check(file.originalUri != null || file.managedCopyPath != null) {
            "Draft attachment ${file.id} has no file location"
        }
        val attachmentId = Uuid.random().toString()
        history.insertAttachment(
            attachmentId, file.name, file.mimeType ?: file.image?.mimeType, file.sourceSizeBytes ?: 0L,
            file.originalUri, file.managedCopyPath, file.storedAttachmentHeader(),
        )
        insertDraftFile(sessionId, index.toLong(), file.id, attachmentId, mode)
    }
    if (previous.any { it.snapshot_id != null }) history.deleteUnreferencedSentAttachments()
    if (previous.isNotEmpty()) history.deleteUnusedDraftFiles(previous.map { it.attachment_id })
}

internal fun ChatDraftQueries.readDraftFiles(sessionId: String): List<File> =
    selectDraftFiles(sessionId).executeAsList().map(SelectDraftFiles::toFile)

internal fun File.storedAttachmentHeader(): String =
    Json.encodeToString(copy(content = "", deferredReading = false, originalUri = null, managedCopyPath = null))

private fun SelectDraftFiles.toFile(): File =
    Json.decodeFromString<File>(checkNotNull(presentation)).copy(
        id = file_id, catalogSnapshotId = snapshot_id, storedAttachmentId = snapshot_id,
        storedSizeBytes = snapshot_size.takeIf { snapshot_id != null },
        deferredReading = transfer_mode == "resource_link",
        originalUri = original_uri, managedCopyPath = managed_path,
    )
