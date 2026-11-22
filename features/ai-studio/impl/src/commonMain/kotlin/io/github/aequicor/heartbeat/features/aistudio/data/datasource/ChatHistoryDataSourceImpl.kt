package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatHistoryDao
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatHistoryDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class ChatHistoryDataSourceImpl(private val dao: ChatHistoryDao) : ChatHistoryDataSource {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun page(sessionId: String, before: Long?, limit: Int) =
        accessStudioRepository { dao.page(sessionId, before, limit).map(ChatEntryRecord::toDomain) }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun position(sessionId: String, messageId: String) =
        accessStudioRepository { dao.position(sessionId, messageId) }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun files(sessionId: String) = accessStudioRepository { dao.files(sessionId).map { it.toDomain() } }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun file(id: String) = accessStudioRepository { dao.file(id).toDomain() }

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
