package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.SendChatMessageUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class ChatTurnInteractorImpl(private val sendMessage: SendChatMessageUseCase) : ChatTurnInteractor {

    private val lock = Mutex()
    private val active = mutableMapOf<String, ActiveTurn>()

    @Throwable(CancellationException::class)
    override suspend fun send(
        sessionId: String,
        text: String,
        attachments: List<ChatAttachment>,
        cancellationRequested: () -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit> {
        val turn = ActiveTurn()
        if (!lock.withLock {
                if (sessionId in active) false else {
                    active[sessionId] = turn
                    true
                }
            }
        ) {
            return Result.failure(ChatTurnException("Дождитесь завершения ответа"))
        }
        return try {
            sendMessage(sessionId, text, attachments, started = { engine ->
                lock.withLock {
                    turn.engine = engine
                    !turn.cancelled && !cancellationRequested()
                }
            }, update)
        } finally {
            withContext(NonCancellable) { lock.withLock { active.remove(sessionId) } }
        }
    }

    @Throwable(CancellationException::class)
    override suspend fun cancel(sessionId: String): Result<Unit> = runCatching {
        val engine = lock.withLock {
            active[sessionId]?.let {
                it.cancelled = true
                it.engine
            }
        }
        engine?.cancel()
        Unit
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun answerPermission(sessionId: String, requestId: String, optionId: String): Result<Unit> = runCatching {
        lock.withLock { active[sessionId]?.engine }?.answerPermission(requestId, optionId)
        Unit
    }.rethrowCancellation()

    private class ActiveTurn {

        var engine: ChatEngineConversationDataSource? = null
        var cancelled = false
    }
}
