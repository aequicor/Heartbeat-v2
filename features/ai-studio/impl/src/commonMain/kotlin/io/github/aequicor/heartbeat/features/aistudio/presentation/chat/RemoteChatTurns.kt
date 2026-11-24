package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable

internal class RemoteChatTurns(
    private val turns: ChatTurnInteractor,
    private val scope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher,
    private val enqueue: (suspend () -> Unit) -> Unit,
) {

    private val jobs = mutableMapOf<String, Job>()
    private val current = mutableMapOf<String, ChatTurn>()
    private val cancellations = MutableStateFlow<Map<String, CompletableDeferred<Unit>>>(emptyMap())

    fun isRunning(chatId: String) = chatId in jobs

    fun start(
        chatId: String,
        submission: Submission,
        render: (ChatTurn) -> Unit,
        complete: (Result<Unit>?) -> Unit,
    ) {
        if (isRunning(chatId)) return
        val cancellationRequested = CompletableDeferred<Unit>()
        cancellations.value += chatId to cancellationRequested
        jobs[chatId] = scope.launch {
            var terminal: ChatTurn? = null
            var outcome: Result<Unit>? = null
            try {
                outcome = withContext(defaultDispatcher) {
                    val cancelTurn = launch {
                        cancellationRequested.await()
                        turns.cancel(chatId)
                    }
                    try {
                        var draftSaved = false
                        val saveDraft = (submission as? Submission.New)?.saveDraft
                        val onUpdate: suspend (ChatTurn) -> Unit = { update ->
                            if (update.running) {
                                if (saveDraft != null && !draftSaved) {
                                    awaitDraftWrite(saveDraft).getOrThrow()
                                    draftSaved = true
                                }
                                enqueue {
                                    current[chatId] = update
                                    render(update)
                                }
                            } else {
                                terminal = update
                            }
                        }
                        val shouldCancel = { cancellationRequested.isCompleted }
                        when (submission) {
                            is Submission.New -> turns.send(
                                chatId, submission.prompt, submission.files.map(File::forTurn), shouldCancel, onUpdate,
                            )
                            is Submission.Retry -> turns.retry(chatId, submission.messageId, shouldCancel, onUpdate)
                        }
                    } catch (cancelled: CancellationException) {
                        val storageFailure = generateSequence<kotlin.Throwable>(cancelled) { it.cause }
                            .flatMap { it.suppressedExceptions }
                            .filterIsInstance<StudioRepositoryException>()
                            .firstOrNull()
                        if (storageFailure != null) outcome = Result.failure(storageFailure)
                        throw cancelled
                    } finally {
                        cancelTurn.cancel()
                    }
                }
            } finally {
                enqueue {
                    jobs.remove(chatId)
                    cancellations.value -= chatId
                    current.remove(chatId)
                    // The domain and this turn's cancellation task have finished before exposing Idle.
                    terminal?.let(render)
                    complete(outcome)
                }
            }
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun awaitDraftWrite(saveDraft: suspend () -> Result<Unit>): Result<Unit> {
        // The first update is emitted after the user message is stored and before the prompt reaches ACP.
        val saved = CompletableDeferred<Result<Unit>>()
        enqueue {
            try {
                saved.complete(saveDraft())
            } catch (cancelled: CancellationException) {
                saved.cancel(cancelled)
                currentCoroutineContext().ensureActive()
            }
        }
        return saved.await()
    }

    fun discard(chatId: String) {
        jobs[chatId]?.cancel()
    }

    fun requestCancellation(chatId: String): Boolean {
        val requested = cancellations.value[chatId] ?: return false
        requested.complete(Unit)
        return true
    }

    fun isStopping(chatId: String): Boolean = cancellations.value[chatId]?.isCompleted == true

    suspend fun answer(chatId: String, requestId: String, allow: Boolean) {
        if (isStopping(chatId)) return
        val pending = current[chatId]?.permission?.takeIf { it.id == requestId } ?: return
        val answer = pending.options.firstOrNull { it.kind == if (allow) "allow_once" else "reject_once" }
        withContext(defaultDispatcher) {
            when {
                answer != null -> turns.answerPermission(chatId, requestId, answer.id)
                !allow -> turns.cancel(chatId)
                else -> Unit
            }
        }
    }

    fun reply(chatId: String): Reply? {
        val turn = current[chatId]?.takeIf { it.running } ?: return null
        if (turn.user.delivery == ChatHistoryEntry.Delivery.Sending) return null
        val reply = turn.reply ?: return null
        val empty = reply.text.isEmpty() && reply.steps.isEmpty() && reply.interim == null &&
            reply.result == null && reply.artifacts.isEmpty()
        return if (turn.permission != null && empty) null else reply.display() as? Reply
    }

    fun activity(chatId: String): AiStudioScreenState.Activity? {
        if (isStopping(chatId)) return AiStudioScreenState.Activity.Stopping
        val turn = current[chatId] ?: return if (isRunning(chatId)) AiStudioScreenState.Activity.Sending else null
        return when {
            !turn.running -> AiStudioScreenState.Activity.Idle
            turn.permission != null -> AiStudioScreenState.Activity.Waiting
            turn.user.delivery == ChatHistoryEntry.Delivery.Sending -> AiStudioScreenState.Activity.Sending
            else -> AiStudioScreenState.Activity.Running
        }
    }

    fun request(chatId: String): Request.Permission? {
        if (isStopping(chatId)) return null
        return current[chatId]?.permission?.let {
            val targets = (listOfNotNull(it.workingDirectory) + it.paths).distinct()
            val access = when (val requested = it.requestedAccess) {
                "read-only" -> Access.ReadOnly.label
                "workspace-write" -> Access.Workspace.label
                "danger-full-access" -> Access.Full.label
                else -> requested
            }
            val scope = listOfNotNull("Только этот вызов", access?.let { label -> "Режим доступа · $label" })
            Request.Permission(
                it.id, it.title, targets.joinToString("\n"), it.command.orEmpty(), scope.joinToString("\n"),
                allowOnce = it.options.any { option -> option.kind == "allow_once" },
            )
        }
    }

    sealed interface Submission {
        data class New(val prompt: String, val files: List<File>, val saveDraft: suspend () -> Result<Unit>) : Submission
        data class Retry(val messageId: String) : Submission
    }
}

private fun File.forTurn() = ChatAttachment(
    id, name, kind, content, date?.let { ChatAttachment.Date(it.year, it.month, it.day, it.time) },
    storedAttachmentId, storedSizeBytes, image?.let { ChatAttachment.Image(it.mimeType, it.thumbnail) },
)
