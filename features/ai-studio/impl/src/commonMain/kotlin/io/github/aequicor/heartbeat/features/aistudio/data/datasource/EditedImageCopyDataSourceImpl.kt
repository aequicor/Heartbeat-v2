package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.features.aistudio.editedPngKind
import io.github.aequicor.heartbeat.features.aistudio.data.dto.EditedImageCopyDraftDto
import io.github.aequicor.heartbeat.features.aistudio.data.workspace.attachmentFileSystem
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid
import io.github.aequicor.heartbeat.features.aistudio.data.dao.EditedImageCopyDao
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.EditedImageCopyDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.EditedImageCopySaveException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class EditedImageCopyDataSourceImpl(
    private val dao: EditedImageCopyDao,
    dataDirectory: String,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : EditedImageCopyDataSource {

    private val draftRoot = dataDirectory.toPath() / "edited-image-drafts"
    private val files = attachmentFileSystem()

    @Throwable(EditedImageCopySaveException::class, CancellationException::class)
    override suspend fun save(sessionId: String, file: AttachmentFile, png: ByteArray, thumbnail: String?): String {
        val path = (file.location as? AttachmentLocation.ManagedCopy)?.relativePath
            ?: throw EditedImageCopySaveException(false, IllegalArgumentException("Edited image copy needs a managed file"))
        val kind = png.editedPngKind()
        val header = buildJsonObject {
            put("id", file.id)
            put("name", file.name)
            put("description", file.description)
            put("kind", kind)
            put("mimeType", "image/png")
            put("base64Encoded", true)
            put("sourceSizeBytes", png.size)
            putJsonObject("image") {
                put("mimeType", "image/png")
                put("thumbnail", thumbnail)
            }
        }
        val draft = EditedImageCopyDraftDto.toDto(file, png.size.toLong(), thumbnail, kind)
        stageDraft(sessionId, draft)
        try {
            dao.save(sessionId, file.id, file.name, file.description, path, png.size.toLong(), header.toString())
        } catch (failure: DaoException) {
            val busy = generateSequence(failure as kotlin.Throwable) { it.cause }.any {
                val message = it.message.orEmpty()
                "SQLITE_BUSY" in message || "database is locked" in message || "SQLITE_LOCKED" in message
            }
            throw EditedImageCopySaveException(busy, failure)
        }
        withContext(ioDispatcher) {
            try {
                files.delete(draftDirectory(sessionId) / "${file.id}.json")
            } catch (_: IOException) {
                // The catalog filters out committed copies if local draft cleanup failed.
            }
        }
        return file.id
    }

    @Throwable(EditedImageCopySaveException::class, CancellationException::class)
    override suspend fun drafts(sessionId: String): List<ChatAttachment> = withContext(ioDispatcher) {
        try {
            files.listOrNull(draftDirectory(sessionId)).orEmpty().filter { it.name.endsWith(".json") }.mapNotNull { path ->
                val dto = try {
                    Json.decodeFromString<EditedImageCopyDraftDto>(files.read(path) { readUtf8() })
                } catch (_: IllegalArgumentException) {
                    return@mapNotNull null
                }
                dto.takeIf { path.name == "${it.id}.json" }?.toDomain()
            }
        } catch (failure: IOException) {
            throw EditedImageCopySaveException(false, failure)
        }
    }

    @Throwable(EditedImageCopySaveException::class, CancellationException::class)
    private suspend fun stageDraft(sessionId: String, draft: EditedImageCopyDraftDto): Unit = withContext(ioDispatcher) {
        val directory = draftDirectory(sessionId)
        val id = Uuid.parseOrNull(draft.id)
            ?: throw EditedImageCopySaveException(false, IllegalArgumentException("Invalid edited image copy ID"))
        val temporary = directory / "$id-${Uuid.random()}.tmp"
        try {
            files.createDirectories(directory)
            files.write(temporary, mustCreate = true) { writeUtf8(Json.encodeToString(draft)) }
            files.atomicMove(temporary, directory / "$id.json")
        } catch (failure: IOException) {
            try {
                files.delete(temporary)
            } catch (cleanup: IOException) {
                failure.addSuppressed(cleanup)
            }
            throw EditedImageCopySaveException(false, failure)
        }
    }

    @Throwable(EditedImageCopySaveException::class)
    private fun draftDirectory(sessionId: String): Path =
        draftRoot / (Uuid.parseOrNull(sessionId)?.toString()
            ?: throw EditedImageCopySaveException(false, IllegalArgumentException("Invalid session ID")))
}
