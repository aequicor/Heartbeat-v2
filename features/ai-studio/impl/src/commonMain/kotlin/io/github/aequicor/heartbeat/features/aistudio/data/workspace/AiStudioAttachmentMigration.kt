package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.data.dao.addAttachmentSnapshot
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.ManagedAttachmentFiles
import io.github.aequicor.heartbeat.features.aistudio.data.model.SentAttachmentRecord
import io.github.aequicor.heartbeat.features.aistudio.db.ChatDraftQueries
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.StudioWorkspaceQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraChat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toPath
import okio.ByteString.Companion.encodeUtf8
import kotlin.Throws as Throwable
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/** Data migration required before the database connection becomes available to the application. */
@Inject
public class AiStudioAttachmentMigration(
    dataDirectory: String,
) {

    private val copies = ManagedAttachmentFiles(dataDirectory.toPath() / "attachments", attachmentFileSystem())

    /** Called by the aggregate schema inside the driver's existing upgrade transaction. */
    @Throwable(Exception::class)
    public fun prepareForCutover(driver: SqlDriver) {
        val upgrade = AttachmentCutoverQueries(driver)
        if (upgrade.claim(14)) migrateDrafts(ChatDraftQueries(driver), upgrade)
        if (upgrade.claim(15)) migrateHistory(ChatQueries(driver), upgrade)
        normalizeSources(upgrade)
    }

    @Throwable(Exception::class)
    public fun migrateStoredWorkspaceHistory(driver: SqlDriver) {
        val workspace = StudioWorkspaceQueries(driver)
        val history = ChatQueries(driver)
        var afterId: String? = null
        while (true) {
            val row = workspace.nextStoredWorkspacePayload(afterId).executeAsOneOrNull() ?: break
            afterId = row.id
            val payload = Json.parseToJsonElement(checkNotNull(row.workspace_payload)).jsonObject
            val messages = payload["messages"]?.jsonArray.orEmpty()
            val interrupted = payload["interrupted"]
            if (messages.isEmpty() && (interrupted == null || interrupted == JsonNull)) continue
            val chat = Json.decodeFromJsonElement<AuroraChat>(payload).recover()
            val archived = archiveLegacyHistory(row.project_id, row.id, chat)
            history.saveHistory(history.chatForSession(row.id).executeAsOne(), archived, previous = null)
            val metadata = JsonObject(payload - setOf("messages", "interrupted")).toString()
            check(workspace.retireStoredWorkspaceHistory(metadata, row.id).value == 1L)
        }
    }

    private fun migrateDrafts(drafts: ChatDraftQueries, upgrade: AttachmentCutoverQueries) {
        var afterId: String? = null
        while (true) {
            val row = upgrade.nextDraft(afterId) ?: break
            val payload = row.payload?.let { Json.parseToJsonElement(it).jsonObject }
            val text = payload?.get("draft")?.jsonPrimitive?.contentOrNull.orEmpty()
            val files = payload?.get("attachments")?.jsonArray.orEmpty().map { Json.decodeFromJsonElement<File>(it) }
            if (row.revision != null || text.isNotEmpty() || files.isNotEmpty()) {
                check(drafts.insertDraft(row.id, row.revision ?: Uuid.random().toString(), text).value == 1L)
                copyLegacyDraftFiles(row.projectId, row.id, files).forEachIndexed { index, file ->
                    val id = Uuid.random().toString()
                    val ref = file.managedCopyPath?.let { "managed:$it" } ?: checkNotNull(file.originalUri)
                    upgrade.insertDraftSource(id, file, ref, Json.encodeToString(file))
                    val mode = if (file.deferredReading) "resource_link" else "resource"
                    check(drafts.insertDraftFile(row.id, index.toLong(), file.id, id, mode).value == 1L)
                }
            }
            val remaining = payload?.let { JsonObject(it - setOf("draft", "attachments")).toString() }
            check(upgrade.retireDraft(row.id, remaining) == 1L)
            afterId = row.id
        }
    }

    private fun migrateHistory(history: ChatQueries, upgrade: AttachmentCutoverQueries) {
        var afterId: String? = null
        while (true) {
            val row = upgrade.nextInlineAttachment(afterId) ?: break
            val file = row.presentation?.let { Json.decodeFromString<File>(it) }
                ?: File(row.id, row.name, "File", "", mimeType = row.mimeType)
            val isBinary = file.base64Encoded || file.image != null
            val content = checkNotNull(row.content) { "Attachment ${row.id} has no stored contents" }
            val bytes = if (isBinary) Base64.decode(content) else content.encodeToByteArray()
            val original = file.originalUri?.nonBlank() ?: row.storageRef.originalFileReference()
            val managed = file.managedCopyPath?.nonBlank() ?: row.storageRef.managedFileReference()
                ?: if (original == null) {
                    val owner = upgrade.owningSession(row.id)
                    copies.storeMigrated(
                        owner?.projectId ?: ProjectId.Ungrouped.value.toString(), owner?.sessionId ?: "migration-unassigned",
                        "history-${row.id.encodeUtf8().sha256().hex()}", file.name, bytes,
                    ).relativePath
                } else null
            if (row.hasDirectParts || !row.hasSnapshots) {
                val snapshot = history.addAttachmentSnapshot(row.id, SentAttachmentRecord(
                    mode = "resource",
                    uri = original ?: "attachment:${row.id}",
                    name = row.name,
                    description = file.description,
                    mimeType = row.mimeType ?: file.mimeType ?: file.image?.mimeType,
                    sizeBytes = bytes.size.toLong(),
                    text = content.takeUnless { isBinary },
                    blob = bytes.takeIf { isBinary },
                ))
                val switched = upgrade.replaceInlineReferences(row.id, snapshot)
                check(!row.hasDirectParts || switched > 0L)
            }
            val ref = managed?.let { "managed:$it" } ?: checkNotNull(original)
            val header = file.copy(
                content = "", managedCopyPath = managed, originalUri = original, base64Encoded = isBinary,
                sourceSizeBytes = file.sourceSizeBytes ?: row.size,
            )
            check(upgrade.retireInlineAttachment(row.id, ref, Json.encodeToString(header)) == 1L)
            afterId = row.id
        }
    }

    private fun normalizeSources(upgrade: AttachmentCutoverQueries) {
        var afterId: String? = null
        while (true) {
            val row = upgrade.nextSource(afterId) ?: break
            val header = row.presentation?.let { Json.parseToJsonElement(it).jsonObject }
            val original = header?.get("originalUri")?.jsonPrimitive?.contentOrNull?.nonBlank()
                ?: row.storageRef.originalFileReference()
            val managed = header?.get("managedCopyPath")?.jsonPrimitive?.contentOrNull?.nonBlank()
                ?: row.storageRef.managedFileReference()
            val remaining = header?.let { JsonObject(it - setOf("originalUri", "managedCopyPath")).toString() }
            check(upgrade.normalizeSource(row.id, original, managed, remaining) == 1L)
            afterId = row.id
        }
    }

    internal fun copyLegacyDraftFiles(projectId: String, sessionId: String, files: List<File>): List<File> =
        files.mapIndexed { index, file ->
            if (!file.originalUri.isNullOrBlank() || !file.managedCopyPath.isNullOrBlank()) {
                file.copy(content = "")
            } else {
                archiveLegacyFile(projectId, sessionId, "draft-$index", file).copy(content = "")
            }
        }

    internal fun archiveLegacyHistory(projectId: String, sessionId: String, chat: AuroraChat): AuroraChat =
        chat.copy(messages = chat.messages.map { message ->
            val key = "workspace-history-${message.id.encodeUtf8().sha256().hex()}"
            var position = 0
            fun archive(file: File): File = archiveLegacyFile(projectId, sessionId, "$key-${position++}", file)

            when (message) {
                is User -> message.copy(files = message.files.map(::archive))
                is Reply -> message.copy(
                    steps = message.steps.map { step ->
                        step.copy(events = step.events.map { event -> event.copy(files = event.files.map(::archive)) })
                    },
                    result = message.result?.let(::archive),
                    artifacts = message.artifacts.map(::archive),
                )
            }
        })

    private fun archiveLegacyFile(projectId: String, sessionId: String, key: String, file: File): File {
        if (!file.originalUri.isNullOrBlank() || !file.managedCopyPath.isNullOrBlank()) return file
        val bytes = if (file.base64Encoded || file.image != null) Base64.decode(file.content) else file.content.encodeToByteArray()
        val location = copies.storeMigrated(projectId, sessionId, key, file.name, bytes)
        return file.copy(managedCopyPath = location.relativePath, sourceSizeBytes = bytes.size.toLong())
    }
}

private fun String.nonBlank() = takeUnless { it.isBlank() }

private fun String.originalFileReference() =
    takeIf { it.startsWith("file:", ignoreCase = true) || it.startsWith("content:", ignoreCase = true) }

private fun String.managedFileReference() = takeIf { it.startsWith("managed:") }?.removePrefix("managed:")?.nonBlank()
