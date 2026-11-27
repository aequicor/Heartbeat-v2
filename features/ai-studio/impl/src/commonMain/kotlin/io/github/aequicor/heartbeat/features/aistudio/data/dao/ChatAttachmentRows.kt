package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.SentAttachmentRecord
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

internal fun ChatQueries.writeAttachmentSnapshot(
    file: ChatTurnMessageRecord.Attachment,
    prepared: SentAttachmentRecord?,
): String {
    if (prepared == null && file.existingId != null) return file.existingId
    val fileId = Uuid.random().toString()
    val link = file.linkUri
    val snapshot = prepared ?: SentAttachmentRecord(
        mode = if (link == null) "resource" else "resource_link",
        uri = link ?: file.originalUri ?: "attachment:$fileId",
        name = file.name,
        description = file.description,
        mimeType = file.mimeType,
        sizeBytes = file.size,
        text = file.content.takeUnless { link != null || file.base64Encoded },
        blob = if (link == null && file.base64Encoded) Base64.decode(file.content) else null,
    )
    if (prepared == null) {
        val existing = findAttachmentSnapshot(
            file.header, snapshot.mode, link, file.originalUri, file.managedPath, snapshot.text, snapshot.blob,
        ).executeAsOneOrNull()
        if (existing != null) return existing
    }
    insertAttachment(fileId, file.name, file.mimeType, file.size, file.originalUri, file.managedPath, file.header)
    return addAttachmentSnapshot(fileId, snapshot)
}

internal fun ChatQueries.addAttachmentSnapshot(fileId: String, snapshot: SentAttachmentRecord): String {
    val id = Uuid.random().toString()
    insertSentAttachment(
        id, fileId, snapshot.mode, snapshot.uri, snapshot.name, snapshot.description,
        snapshot.mimeType, snapshot.sizeBytes, snapshot.text, snapshot.blob,
    )
    return id
}

internal fun ChatQueries.fileHeader(snapshotId: String): ChatEntryRecord.Attachment =
    snapshotHeader(snapshotId) { id, size, header, mime, mode, uri, original, managed ->
        ChatEntryRecord.Attachment(id, size, header, null, mime, mode, uri, originalUri = original, managedPath = managed)
    }.executeAsOne()

internal fun ChatQueries.fullAttachment(snapshotId: String): ChatEntryRecord.Attachment =
    storedAttachmentSnapshot(snapshotId) { id, size, header, mime, mode, uri, original, managed, text, blob ->
        val content = text ?: blob?.let { Base64.encode(it) }
        ChatEntryRecord.Attachment(id, size, header, content, mime, mode, uri, blob != null, original, managed)
    }.executeAsOne()

internal fun ChatQueries.readFileCatalog(sessionId: String): List<ChatEntryRecord.Attachment> =
    chatFileHeaders(chatForSession(sessionId).executeAsOne()) { id, size, header, mime, mode, uri, original, managed, addedAt ->
        ChatEntryRecord.Attachment(
            id, size, header, null, mime, mode, uri,
            originalUri = original, managedPath = managed, addedAt = addedAt.takeIf(String::isNotEmpty),
        )
    }.executeAsList()
