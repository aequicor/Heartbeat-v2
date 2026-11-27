package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatInterruptionLabels
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatHistoryDao {

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun page(sessionId: String, before: Long?, limit: Int, interruption: ChatInterruptionLabels): List<ChatEntryRecord>

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun position(sessionId: String, messageId: String): Long?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun files(sessionId: String): List<ChatEntryRecord.Attachment>

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun file(id: String): ChatEntryRecord.Attachment

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun removeUnavailableAttachment(sessionId: String, snapshotId: String)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun partBody(id: Long): String

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun revision(chatId: String, revisionId: Long): ChatEntryRecord.Revision

    /** Creates an independent studio session through the selected, committed revision (inclusive). */
    @Throwable(DaoException::class, CancellationException::class)
    suspend fun fork(chatId: String, revisionId: Long, title: String): String

    /** Replaces the selected user revision. The expected revision prevents overwriting a concurrent edit. */
    @Throwable(DaoException::class, CancellationException::class)
    suspend fun editText(chatId: String, revisionId: Long, text: String, keepContinuation: Boolean)
}
