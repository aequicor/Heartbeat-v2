package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatHistoryInteractor {

    @Throwable(CancellationException::class)
    suspend fun page(sessionId: String, before: Long? = null, anchor: String? = null): Result<ChatHistoryEntry.Page>

    @Throwable(CancellationException::class)
    suspend fun files(sessionId: String): Result<List<ChatAttachment>>

    @Throwable(CancellationException::class)
    suspend fun file(id: String): Result<ChatAttachment>

    @Throwable(CancellationException::class)
    suspend fun partBody(id: Long): Result<String>

    @Throwable(CancellationException::class)
    suspend fun fork(chatId: String, revisionId: Long, title: String): Result<String>

    @Throwable(CancellationException::class)
    suspend fun editText(chatId: String, revisionId: Long, text: String, keepContinuation: Boolean): Result<Unit>
}
