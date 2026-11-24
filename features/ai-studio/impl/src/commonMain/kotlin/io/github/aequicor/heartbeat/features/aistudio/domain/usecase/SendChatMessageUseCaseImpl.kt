package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPrompt
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

@Inject
internal class SendChatMessageUseCaseImpl(
    private val conversations: ChatConversationDataSource,
    private val prepareChatContext: PrepareChatContextUseCase,
) : SendChatMessageUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(
        sessionId: String,
        text: String,
        attachments: List<ChatAttachment>,
        started: suspend (ChatEngineConversationDataSource) -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit> = runCatching {
        val prompt = text.trim()
        require(ChatPrompt.canSend(prompt, attachments.isNotEmpty(), hasActiveTurn = false))
        val user = newMessage(prompt, attachments)
        withContext(NonCancellable) { conversations.save(sessionId, listOf(user)) }
        deliver(sessionId, user, started, update)
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun retry(
        sessionId: String,
        messageId: String,
        started: suspend (ChatEngineConversationDataSource) -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit> = runCatching {
        val user = withContext(NonCancellable) { conversations.reserveRetry(sessionId, messageId) }
            ?: throw ChatTurnException("Повторная отправка недоступна")
        deliver(sessionId, user, started, update)
    }.rethrowCancellation()

    private suspend fun deliver(
        sessionId: String,
        user: ChatHistoryEntry.User,
        started: suspend (ChatEngineConversationDataSource) -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ) {
        var turn = ChatTurn(user = user)
        val replyId = Uuid.random().toString()
        var savedUser = user
        val save: suspend (ChatTurn) -> Unit = { snapshot ->
            val messages = listOfNotNull(snapshot.user.takeUnless { it == savedUser }, snapshot.reply)
            if (messages.isNotEmpty()) conversations.save(sessionId, messages)
            savedUser = snapshot.user
        }
        var dispatchStarted = false
        try {
            currentCoroutineContext().ensureActive()
            update(turn)
            val conversation = prepareChatContext(sessionId).getOrThrow()
            if (started(conversation)) {
                dispatchStarted = true
                coroutineScope {
                    val checkpointLock = Mutex()
                    var scheduledCheckpoint: Job? = null
                    var lastWrite = TimeSource.Monotonic.markNow()
                    var savedReply: ChatHistoryEntry.Reply? = null

                    suspend fun checkpoint() = withContext(NonCancellable) {
                        val reply = checkNotNull(turn.reply).takeIf { it.hasContent || savedReply != null }
                        try {
                            save(turn.copy(reply = reply))
                            savedReply = reply
                        } catch (_: StudioRepositoryException) {
                            // A later snapshot can save the full turn without interrupting the engine.
                        }
                        lastWrite = TimeSource.Monotonic.markNow()
                    }

                    val result = runCatching {
                        conversation.send(user.text, user.files) { progress ->
                            checkpointLock.withLock {
                                turn = turn.updated(replyId, progress)
                                if (!turn.running) {
                                    scheduledCheckpoint?.cancel()
                                    scheduledCheckpoint = null
                                    finishTurn(turn, save, update)
                                    return@withLock
                                }
                                val reply = checkNotNull(turn.reply)
                                val withdrawsSavedContent = savedReply?.let(reply::withdrawsContentFrom) == true
                                if (!withdrawsSavedContent) update(turn)
                                val firstContent = reply.hasContent && savedReply?.hasContent != true
                                val firstAnswerText = reply.text.isNotBlank() && savedReply?.text.isNullOrBlank()
                                val receivedArtifact = reply.fileCount > (savedReply?.fileCount ?: 0)
                                val accepted = turn.user.delivery == ChatHistoryEntry.Delivery.Sent && savedUser != turn.user
                                val changed = savedReply != null && savedReply != reply
                                val remaining = StreamCheckpointInterval - lastWrite.elapsedNow()
                                if (accepted || withdrawsSavedContent || firstContent || firstAnswerText || receivedArtifact ||
                                    (changed && remaining <= Duration.ZERO)
                                ) {
                                    scheduledCheckpoint?.cancel()
                                    scheduledCheckpoint = null
                                    checkpoint()
                                } else if (changed && scheduledCheckpoint == null) {
                                    scheduledCheckpoint = launch {
                                        delay(remaining)
                                        checkpointLock.withLock {
                                            scheduledCheckpoint = null
                                            if (turn.reply != savedReply) checkpoint()
                                        }
                                    }
                                }
                                // Keep a running correction pending until the matching draft is durable.
                                if (withdrawsSavedContent && savedReply === reply) update(turn)
                            }
                        }
                    }
                    withContext(NonCancellable) {
                        checkpointLock.withLock { scheduledCheckpoint?.cancel() }
                    }
                    result
                }.getOrThrow()
            } else {
                turn = turn.interrupted("Отправка отменена", retryAllowed = true)
                finishTurn(turn, save, update)
            }
        } catch (cancelled: CancellationException) {
            val error = if (dispatchStarted) "Результат отправки неизвестен" else "Отправка отменена"
            if (turn.running) {
                finishInterruptedTurn(turn.interrupted(error, retryAllowed = !dispatchStarted), cancelled, save, update)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (turn.running) {
                val turnFailure = failure as? ChatTurnException
                val notDispatched = !dispatchStarted || turnFailure?.notDispatched == true
                val error = when {
                    !notDispatched -> "Результат отправки неизвестен"
                    failure is StudioRepositoryException -> "Не удалось сохранить черновик"
                    else -> turnFailure?.message ?: "Не удалось открыть сессию движка"
                }
                finishTurn(turn.interrupted(error, ChatHistoryEntry.ReplyStatus.Failed, retryAllowed = notDispatched), save, update)
            }
            throw failure
        }
    }

    private suspend fun finishTurn(
        turn: ChatTurn,
        save: suspend (ChatTurn) -> Unit,
        update: suspend (ChatTurn) -> Unit,
    ) {
        try {
            saveFinalTurn(turn, save)
        } finally {
            update(turn)
        }
    }

    private suspend fun finishInterruptedTurn(
        stopped: ChatTurn,
        cancelled: CancellationException,
        save: suspend (ChatTurn) -> Unit,
        update: suspend (ChatTurn) -> Unit,
    ) {
        withContext(NonCancellable) {
            try {
                saveFinalTurn(stopped, save)
            } catch (failure: Exception) {
                if (failure !== cancelled) cancelled.addSuppressed(failure)
            }
            try {
                update(stopped)
            } catch (failure: Exception) {
                if (failure !== cancelled) cancelled.addSuppressed(failure)
            }
        }
    }

    private suspend fun saveFinalTurn(turn: ChatTurn, save: suspend (ChatTurn) -> Unit) = withContext(NonCancellable) {
        try {
            save(turn)
        } catch (_: StudioRepositoryException) {
            save(turn)
        }
    }

    private fun newMessage(text: String, attachments: List<ChatAttachment>) = ChatHistoryEntry.User(
        id = Uuid.random().toString(), time = Clock.System.now().toString().substringAfter('T').take(5),
        text = text, promptId = null, files = attachments, delivery = ChatHistoryEntry.Delivery.Sending,
        error = null, answers = false, scenario = ChatHistoryEntry.Scenario.Standard, access = ChatHistoryEntry.Access.Workspace,
        retryAllowed = false,
    )
}

private val StreamCheckpointInterval = 250.milliseconds

private val ChatHistoryEntry.Reply.hasContent: Boolean
    get() = text.isNotBlank() || !interim.isNullOrBlank() || steps.isNotEmpty() || artifacts.isNotEmpty()

private val ChatHistoryEntry.Reply.fileCount: Int
    get() = artifacts.size + steps.sumOf { step -> step.events.sumOf { it.files.size } }

private fun ChatHistoryEntry.Reply.withdrawsContentFrom(saved: ChatHistoryEntry.Reply): Boolean {
    if (!text.startsWith(saved.text) || !interim.orEmpty().startsWith(saved.interim.orEmpty())) return true
    if (saved.steps.isEmpty()) return false
    val currentSteps = steps.associateBy(ChatHistoryEntry.Step::id)
    val withdrawsStepText = saved.steps.any { previous ->
        val current = currentSteps[previous.id]
        !current?.details.orEmpty().startsWith(previous.details) ||
            !current?.result.orEmpty().startsWith(previous.result.orEmpty())
    }
    if (withdrawsStepText) return true
    val savedThoughts = saved.steps.flatMap(ChatHistoryEntry.Step::events)
        .filter { it.kind == ChatHistoryEntry.EventKind.Thinking }
    if (savedThoughts.isEmpty()) return false
    val currentDetails = steps.flatMap(ChatHistoryEntry.Step::events).associate { it.id to it.details }
    return savedThoughts.any { thought -> currentDetails[thought.id]?.startsWith(thought.details) != true }
}

private fun ChatTurn.interrupted(
    error: String,
    status: ChatHistoryEntry.ReplyStatus = ChatHistoryEntry.ReplyStatus.Stopped,
    retryAllowed: Boolean = false,
): ChatTurn {
    val delivered = user.delivery == ChatHistoryEntry.Delivery.Sent
    return copy(
        user = if (delivered) user else user.copy(
            delivery = ChatHistoryEntry.Delivery.Failed, error = error, retryAllowed = retryAllowed,
        ),
        reply = reply?.takeIf { delivered }?.let { current ->
            current.copy(status = status, steps = current.steps.map { it.stop() })
        },
        permission = null,
        running = false,
    )
}

private fun ChatTurn.updated(replyId: String, progress: ChatEngineTurn): ChatTurn {
    val status = when (progress.status) {
        ChatEngineTurn.Status.Running -> ChatHistoryEntry.ReplyStatus.Working
        ChatEngineTurn.Status.Completed -> ChatHistoryEntry.ReplyStatus.Completed
        ChatEngineTurn.Status.Stopped -> ChatHistoryEntry.ReplyStatus.Stopped
        ChatEngineTurn.Status.Failed -> ChatHistoryEntry.ReplyStatus.Failed
        ChatEngineTurn.Status.CancelledBeforeDispatch -> return interrupted("Отправка отменена", retryAllowed = true)
        ChatEngineTurn.Status.Rejected -> {
            val error = when (progress.rejection) {
                ChatEngineTurn.Rejection.ImageUnsupported -> "Выбранная модель или подключение не поддерживает изображения"
                null -> "Движок отклонил сообщение"
            }
            return interrupted(error, retryAllowed = true)
        }
        ChatEngineTurn.Status.SendFailed -> return interrupted("Результат отправки неизвестен")
    }
    val assistant = ChatHistoryEntry.Reply(
        replyId, user.time, progress.text, user.id, progress.interim, status,
        if (status == ChatHistoryEntry.ReplyStatus.Working) progress.steps else progress.steps.map { it.stop() }, null,
        retryAllowed = false,
        failure = progress.failure.takeIf { status == ChatHistoryEntry.ReplyStatus.Failed },
        artifacts = (reply?.artifacts.orEmpty() + progress.artifacts).distinctBy(ChatAttachment::id),
    )
    val delivered = user.delivery == ChatHistoryEntry.Delivery.Sent || progress.accepted ||
        assistant.hasContent || progress.permission != null
    val running = status == ChatHistoryEntry.ReplyStatus.Working
    val hasReply = reply != null || assistant.hasContent || status == ChatHistoryEntry.ReplyStatus.Failed
    val delivery = when {
        status == ChatHistoryEntry.ReplyStatus.Stopped && !delivered -> ChatHistoryEntry.Delivery.Failed
        running && !delivered -> ChatHistoryEntry.Delivery.Sending
        else -> ChatHistoryEntry.Delivery.Sent
    }
    return copy(
        user = user.copy(
            delivery = delivery,
            error = "Отправка отменена".takeIf { delivery == ChatHistoryEntry.Delivery.Failed },
        ),
        reply = assistant.takeIf { delivery != ChatHistoryEntry.Delivery.Failed && (running || hasReply) },
        permission = progress.permission,
        running = running,
    )
}
