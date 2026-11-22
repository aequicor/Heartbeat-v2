package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPrompt
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

@Inject
internal class SendChatMessageUseCaseImpl(
    private val conversations: ChatConversationDataSource,
    private val engines: ChatEngineDataSource,
    private val sessions: SessionsDataSource,
    private val projects: ProjectDataSource,
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
        var turn = ChatTurn(user = newMessage(prompt, attachments))
        val replyId = Uuid.random().toString()
        conversations.save(sessionId, listOf(turn.user))
        var sent = false
        try {
            update(turn)
            val conversation = resume(sessionId)
            if (started(conversation)) {
                sent = true
                var lastWrite = TimeSource.Monotonic.markNow()
                conversation.send(prompt, attachments) { progress ->
                    val next = turn.updated(replyId, progress)
                    if (turn.reply == null || !next.running || lastWrite.elapsedNow() >= 250.milliseconds) {
                        conversations.save(sessionId, listOfNotNull(next.user, next.reply))
                        lastWrite = TimeSource.Monotonic.markNow()
                    }
                    turn = next
                    update(next)
                }
            } else {
                turn = turn.copy(user = turn.user.copy(delivery = ChatHistoryEntry.Delivery.Failed, error = "Отправка отменена"),
                    running = false)
                conversations.save(sessionId, listOf(turn.user))
                update(turn)
            }
        } catch (cancelled: CancellationException) {
            finishInterruptedTurn(sessionId, turn, update)
            throw cancelled
        } catch (failure: Exception) {
            if (turn.running) {
                val error = when {
                    sent -> "Результат отправки неизвестен"
                    failure is StudioRepositoryException -> "Не удалось сохранить черновик"
                    else -> (failure as? ChatTurnException)?.message ?: "Не удалось открыть сессию движка"
                }
                val stopped = turn.interrupted(error)
                val persisted = runCatching {
                    conversations.save(sessionId, listOfNotNull(stopped.user, stopped.reply))
                }.rethrowCancellation()
                update(stopped)
                persisted.getOrThrow()
            }
            throw failure
        }
    }.rethrowCancellation()

    private suspend fun finishInterruptedTurn(
        sessionId: String,
        turn: ChatTurn,
        update: suspend (ChatTurn) -> Unit,
    ) {
        if (!turn.running) return
        withContext(NonCancellable) {
            val stopped = turn.interrupted("Результат отправки неизвестен")
            runCatching {
                conversations.save(sessionId, listOfNotNull(stopped.user, stopped.reply))
                update(stopped)
            }.rethrowCancellation()
        }
    }

    private suspend fun resume(sessionId: String): ChatEngineConversationDataSource {
        val settings = conversations.find(sessionId)
            ?: throw ChatTurnException("Чат не найден или перемещён в архив")
        if (settings.contextStale) throw ChatTurnException("Контекст чата требует восстановления")
        val config = settings.configuration ?: throw ChatTurnException("Для чата не выбран движок")
        val remoteId = settings.engineSessionId ?: throw ChatTurnException("Сессия движка недоступна")
        val session = sessions.findById(SessionId.parse(sessionId))
            ?.takeIf { it.status == SessionStatus.ACTIVE } ?: throw ChatTurnException("Чат недоступен")
        val directory = when (val project = projects.findById(session.projectId)) {
            is Project.WithDirectory -> project.workingDirectory?.toString()
                ?: throw ChatTurnException("Рабочая папка недоступна движку")
            is Project.WithoutDirectory -> null
            null -> throw ChatTurnException("Проект недоступен")
        }
        val conversation = engines.resume(config.engineId, remoteId, directory)
        conversation.restoreOption(conversation.settings.model, config.model)
        conversation.restoreOption(conversation.settings.reasoning, config.reasoning)
        return conversation
    }

    private suspend fun ChatEngineConversationDataSource.restoreOption(
        selector: ChatEngineOptions.Selector?,
        value: String?,
    ) {
        if (value == null) return
        if (selector == null || selector.choices.none { it.id == value }) {
            throw ChatTurnException("Сохранённый параметр чата больше недоступен")
        }
        if (selector.current == value) return
        val applied = select(selector.id, value)
        if (listOfNotNull(applied.model, applied.reasoning).none { it.id == selector.id && it.current == value }) {
            throw ChatTurnException("Движок отклонил сохранённый параметр чата")
        }
    }

    private fun newMessage(text: String, attachments: List<ChatAttachment>) = ChatHistoryEntry.User(
        id = Uuid.random().toString(), time = Clock.System.now().toString().substringAfter('T').take(5),
        text = text, promptId = null, files = attachments, delivery = ChatHistoryEntry.Delivery.Sending,
        error = null, answers = false, scenario = ChatHistoryEntry.Scenario.Standard, access = ChatHistoryEntry.Access.Workspace,
        retryAllowed = false,
    )
}

private fun ChatTurn.interrupted(error: String) = copy(
    user = user.copy(delivery = ChatHistoryEntry.Delivery.Failed, error = error),
    reply = reply?.let { current ->
        current.copy(status = ChatHistoryEntry.ReplyStatus.Stopped, steps = current.steps.map { it.stop() })
    },
    permission = null,
    running = false,
)

private fun ChatTurn.updated(replyId: String, progress: ChatEngineTurn): ChatTurn {
    val status = when (progress.status) {
        ChatEngineTurn.Status.Running -> ChatHistoryEntry.ReplyStatus.Working
        ChatEngineTurn.Status.Completed -> ChatHistoryEntry.ReplyStatus.Completed
        ChatEngineTurn.Status.Stopped -> ChatHistoryEntry.ReplyStatus.Stopped
        ChatEngineTurn.Status.Failed -> ChatHistoryEntry.ReplyStatus.Failed
    }
    val failed = status == ChatHistoryEntry.ReplyStatus.Failed
    val delivery = when {
        failed -> ChatHistoryEntry.Delivery.Failed
        status == ChatHistoryEntry.ReplyStatus.Stopped && reply == null -> ChatHistoryEntry.Delivery.Failed
        status == ChatHistoryEntry.ReplyStatus.Working -> ChatHistoryEntry.Delivery.Sending
        else -> ChatHistoryEntry.Delivery.Sent
    }
    val assistant = ChatHistoryEntry.Reply(
        replyId, user.time, progress.text, user.id, null, status,
        if (status == ChatHistoryEntry.ReplyStatus.Working) progress.steps else progress.steps.map { it.stop() }, null,
        retryAllowed = false,
    )
    return copy(
        user = user.copy(
            delivery = delivery,
            error = when {
                failed -> "Результат отправки неизвестен"
                delivery == ChatHistoryEntry.Delivery.Failed -> "Отправка отменена"
                else -> null
            },
        ),
        reply = assistant.takeIf { status == ChatHistoryEntry.ReplyStatus.Working || reply != null ||
            progress.text.isNotEmpty() || progress.steps.isNotEmpty() },
        permission = progress.permission,
        running = status == ChatHistoryEntry.ReplyStatus.Working,
    )
}
