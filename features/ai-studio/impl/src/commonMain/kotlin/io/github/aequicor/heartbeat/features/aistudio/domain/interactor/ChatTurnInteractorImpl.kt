package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.chatDirectory
import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ApplyChatConfigurationUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.SendChatMessageUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class ChatTurnInteractorImpl(
    private val sessions: SessionsDataSource,
    private val projects: ProjectDataSource,
    private val engines: ChatEngineDataSource,
    private val sendMessage: SendChatMessageUseCase,
    private val applyConfiguration: ApplyChatConfigurationUseCase,
) : ChatTurnInteractor {

    private val lock = Mutex()
    private val active = mutableMapOf<String, ActiveTurn>()

    @Throwable(CancellationException::class)
    override suspend fun send(
        sessionId: String,
        messageId: String,
        text: String,
        attachments: List<ChatAttachment>,
        expectedDraftRevision: String,
        cancellationRequested: () -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit> = runTurn(sessionId, cancellationRequested, update) { started, publish ->
        sendMessage(sessionId, messageId, text, attachments, expectedDraftRevision, started, publish)
    }

    @Throwable(CancellationException::class)
    override suspend fun retry(
        sessionId: String,
        messageId: String,
        expectedFailedRevisionId: Long,
        cancellationRequested: () -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit> = runTurn(sessionId, cancellationRequested, update) { started, publish ->
        sendMessage.retry(sessionId, messageId, expectedFailedRevisionId, started, publish)
    }

    private suspend fun runTurn(
        sessionId: String,
        cancellationRequested: () -> Boolean,
        update: suspend (ChatTurn) -> Unit,
        send: suspend (suspend (ChatEngineConversationDataSource) -> Boolean, suspend (ChatTurn) -> Unit) -> Result<Unit>,
    ): Result<Unit> = withIdleChat(sessionId) { turn ->
        coroutineScope {
            val preparation = coroutineContext.job
            send(
                { engine ->
                    lock.withLock {
                        turn.preparation = null
                        turn.engine = engine
                        !turn.cancelled && !cancellationRequested()
                    }
                },
                { snapshot ->
                    update(snapshot)
                    if (snapshot.running && snapshot.reply == null) {
                        // Once the prompt is committed, cancellation can interrupt engine preparation.
                        val stopped = lock.withLock {
                            turn.preparation = preparation
                            turn.cancelled || cancellationRequested()
                        }
                        if (stopped) preparation.cancel()
                        preparation.ensureActive()
                    }
                },
            )
        }
    }

    @Throwable(CancellationException::class)
    override suspend fun applyConfiguration(sessionId: String, configuration: ChatConfiguration): Result<Unit> =
        withIdleChat(sessionId) { applyConfiguration.invoke(sessionId, configuration) }

    @Throwable(CancellationException::class)
    override suspend fun selectSecurityMode(sessionId: String, value: String?): Result<Unit> = withIdleChat(sessionId) {
        runCatching {
            val directory = if (value == null) chatDirectory(sessionId, sessions, projects) else null
            engines.selectSecurityMode(sessionId, value, directory)
        }.rethrowCancellation()
    }

    private suspend fun withIdleChat(
        sessionId: String,
        operation: suspend (ActiveTurn) -> Result<Unit>,
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
            operation(turn)
        } finally {
            withContext(NonCancellable) { lock.withLock { active.remove(sessionId) } }
        }
    }

    @Throwable(CancellationException::class)
    override suspend fun cancel(sessionId: String): Result<Unit> = runCatching {
        val (preparation, engine) = lock.withLock {
            active[sessionId]?.let {
                it.cancelled = true
                it.preparation to it.engine
            }
        } ?: return@runCatching
        preparation?.cancel()
        engine?.cancel()
        Unit
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun answerPermission(sessionId: String, requestId: String, optionId: String): Result<Unit> = runCatching {
        lock.withLock { active[sessionId]?.engine }?.answerPermission(requestId, optionId)
        Unit
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun answerQuestionnaire(
        sessionId: String,
        requestId: String,
        answer: QuestionnaireAnswer,
    ): Result<Unit> = runCatching {
        lock.withLock { active[sessionId]?.engine }?.answerQuestionnaire(requestId, answer)
        Unit
    }.rethrowCancellation()

    private class ActiveTurn {

        var preparation: Job? = null
        var engine: ChatEngineConversationDataSource? = null
        var cancelled = false
    }
}
