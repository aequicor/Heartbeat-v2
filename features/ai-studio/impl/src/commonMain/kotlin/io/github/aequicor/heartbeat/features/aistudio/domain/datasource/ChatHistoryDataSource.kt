package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatHistoryDataSource {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun source(sessionId: String, displayId: String?, revisionId: Long?): ChatHistoryEntry?

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun page(sessionId: String, before: Long?, limit: Int): List<ChatHistoryEntry>

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun position(sessionId: String, messageId: String): Long?

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun files(sessionId: String): List<ChatAttachment>

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun file(id: String): ChatAttachment

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun removeUnavailableAttachment(sessionId: String, snapshotId: String)

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun partBody(id: Long): String

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun revision(chatId: String, revisionId: Long): ChatHistoryEntry.Revision

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun fork(chatId: String, revisionId: Long, title: String): String

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun editText(chatId: String, revisionId: Long, text: String, keepContinuation: Boolean): Unit
}
