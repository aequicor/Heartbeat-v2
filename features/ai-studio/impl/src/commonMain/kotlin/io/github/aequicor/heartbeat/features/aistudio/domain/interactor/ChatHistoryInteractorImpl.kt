package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatHistoryDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class ChatHistoryInteractorImpl(private val history: ChatHistoryDataSource) : ChatHistoryInteractor {

    @Throwable(CancellationException::class)
    override suspend fun page(sessionId: String, before: Long?, anchor: String?): Result<ChatHistoryEntry.Page> = runCatching {
        val target = anchor?.let { history.position(sessionId, it) }
        val entries = mutableListOf<ChatHistoryEntry>()
        var cursor = before
        do {
            val rows = history.page(sessionId, cursor, PageSize + 1)
            val visible = rows.take(PageSize)
            entries.addAll(visible)
            cursor = visible.lastOrNull()?.position?.takeIf { rows.size > PageSize }
        } while (target != null && cursor != null && cursor > target)
        ChatHistoryEntry.Page(entries, cursor)
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun files(sessionId: String): Result<List<ChatAttachment>> =
        runCatching { history.files(sessionId).distinctBy { it.storageId ?: it.id } }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun file(id: String): Result<ChatAttachment> = runCatching { history.file(id) }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun partBody(id: Long): Result<String> = runCatching { history.partBody(id) }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun fork(chatId: String, revisionId: Long, title: String): Result<String> = runCatching {
        history.revision(chatId, revisionId).requireCommitted()
        history.fork(chatId, revisionId, title)
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun editText(chatId: String, revisionId: Long, text: String, keepContinuation: Boolean): Result<Unit> =
        runCatching {
            history.revision(chatId, revisionId).requireEditable(text)
            history.editText(chatId, revisionId, text, keepContinuation)
        }.rethrowCancellation()

    private companion object {

        const val PageSize = 64
    }
}
