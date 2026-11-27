package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.attachmentImagePreviewLimit
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatHistoryDao
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatInterruptionLabels
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatHistoryDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.InterruptedReasoningTitle
import io.github.aequicor.heartbeat.features.aistudio.domain.model.InterruptedSendError
import io.github.aequicor.heartbeat.features.aistudio.domain.model.InterruptedToolSummary
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

@Inject
internal class ChatHistoryDataSourceImpl(
    private val dao: ChatHistoryDao,
    private val files: ChatAttachmentDataSource,
) : ChatHistoryDataSource {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun source(sessionId: String, displayId: String?, revisionId: Long?): ChatHistoryEntry? =
        accessStudioRepository {
            val record = dao.source(sessionId, displayId, revisionId) ?: return@accessStudioRepository null
            val entry = record.toDomain(keepAttachmentReference = true)
            val links = record.parts.mapNotNull { it.attachment }
                .filter { it.transferMode == "resource_link" && it.uri != null }
                .associateBy { it.id }
            suspend fun hydrate(attachment: ChatAttachment): ChatAttachment {
                val link = links[attachment.storageId]?.uri ?: return attachment
                return try {
                    readLinkedAttachment(attachment, link)
                } catch (_: StudioRepositoryException) {
                    // Keep the missing attachment so the sender can surface the error before committing the draft.
                    attachment
                }
            }
            val message = when (val original = entry.message) {
                is ChatHistoryEntry.User -> original.copy(files = original.files.map { hydrate(it) })
                is ChatHistoryEntry.Reply -> original.copy(
                    result = original.result?.let { hydrate(it) },
                    artifacts = original.artifacts.map { hydrate(it) },
                    steps = original.steps.map { step ->
                        step.copy(events = step.events.map { it.copy(files = it.files.map { file -> hydrate(file) }) })
                    },
                )
            }
            entry.copy(message = message)
        }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun page(sessionId: String, before: Long?, limit: Int) =
        accessStudioRepository { dao.page(sessionId, before, limit, interruptionLabels).map(ChatEntryRecord::toDomain) }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun position(sessionId: String, messageId: String) =
        accessStudioRepository { dao.position(sessionId, messageId) }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun files(sessionId: String) = accessStudioRepository { dao.files(sessionId).map { it.toDomain() } }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun removeUnavailableAttachment(sessionId: String, snapshotId: String): Unit = accessStudioRepository {
        dao.removeUnavailableAttachment(sessionId, snapshotId)
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun file(id: String) = accessStudioRepository {
        val snapshot = dao.file(id)
        val file = snapshot.toDomain()
        if (snapshot.transferMode != "resource_link") return@accessStudioRepository file
        val uri = checkNotNull(snapshot.uri)
        val reference = if (uri == "edited-image:${file.id}") {
            files.reference(checkNotNull(file.source).location)
        } else uri
        readLinkedAttachment(file, reference)
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    private suspend fun readLinkedAttachment(file: ChatAttachment, uri: String): ChatAttachment = try {
        val limit = if (ChatImage.forMimeType(file.mimeType) != null) attachmentImagePreviewLimit(file.kind) else ChatImage.MaxBytes
        val bytes = files.read(uri, limit).second
        check(bytes.size <= limit)
        val format = ChatImage.identify(bytes)
        val text = if (format == null) {
            runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }
                .getOrNull()?.takeUnless { '\u0000' in it }
        } else null
        file.copy(
            content = text ?: Base64.encode(bytes),
            base64Encoded = text == null,
            image = format?.let { ChatAttachment.Image(it.mimeType, file.image?.thumbnail) },
            mimeType = format?.mimeType ?: file.mimeType,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw StudioRepositoryException("Не удалось открыть «${file.name}»", failure)
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun partBody(id: Long) = accessStudioRepository { dao.partBody(id) }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun revision(chatId: String, revisionId: Long) = accessStudioRepository {
        dao.revision(chatId, revisionId).let { ChatHistoryEntry.Revision(it.role, it.status, it.hasAttachments) }
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun fork(chatId: String, revisionId: Long, title: String) =
        accessStudioRepository { dao.fork(chatId, revisionId, title) }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun editText(chatId: String, revisionId: Long, text: String, keepContinuation: Boolean) =
        accessStudioRepository { dao.editText(chatId, revisionId, text, keepContinuation) }
}

private val interruptionLabels = ChatInterruptionLabels(InterruptedSendError, InterruptedReasoningTitle, InterruptedToolSummary)
