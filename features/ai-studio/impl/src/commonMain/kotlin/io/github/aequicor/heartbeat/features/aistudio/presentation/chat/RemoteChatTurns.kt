package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
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
    private val cancellations = MutableStateFlow<Map<String, MutableStateFlow<Boolean>>>(emptyMap())

    fun isRunning(chatId: String) = chatId in jobs

    fun start(
        chatId: String,
        prompt: String,
        files: List<File>,
        saveDraft: suspend () -> Result<Unit>,
        render: (ChatTurn) -> Unit,
        complete: (Result<Unit>?) -> Unit,
    ) {
        if (isRunning(chatId)) return
        val cancelled = MutableStateFlow(false)
        cancellations.value += chatId to cancelled
        jobs[chatId] = scope.launch {
            var terminal: ChatTurn? = null
            var outcome: Result<Unit>? = null
            try {
                outcome = withContext(defaultDispatcher) {
                    var draftSaved = false
                    turns.send(chatId, prompt, files.map(File::forTurn), { cancelled.value }) { update ->
                        if (update.running) {
                            if (!draftSaved) {
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
                }
            } finally {
                enqueue {
                    jobs.remove(chatId)
                    cancellations.value -= chatId
                    current.remove(chatId)
                    // Expose Idle only once the domain has released the slot for the next send.
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
        requested.value = true
        enqueue {
            if (cancellations.value[chatId] === requested) {
                withContext(defaultDispatcher) { turns.cancel(chatId) }
            }
        }
        return true
    }

    suspend fun answer(chatId: String, requestId: String, allow: Boolean) {
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

    fun reply(chatId: String) =
        (current[chatId]?.takeIf { it.running }?.reply?.display() as? AiStudioScreenState.Reply)

    fun activity(chatId: String): AiStudioScreenState.Activity? {
        val turn = current[chatId] ?: return if (isRunning(chatId)) AiStudioScreenState.Activity.Sending else null
        return when {
            !turn.running -> AiStudioScreenState.Activity.Idle
            turn.permission != null -> AiStudioScreenState.Activity.Waiting
            turn.reply == null -> AiStudioScreenState.Activity.Sending
            else -> AiStudioScreenState.Activity.Running
        }
    }

    fun request(chatId: String): Request.Permission? = current[chatId]?.permission?.let {
        Request.Permission(
            it.id, it.title, "", "", "Текущий запрос",
            allowOnce = it.options.any { option -> option.kind == "allow_once" },
        )
    }
}

private fun File.forTurn() = ChatAttachment(
    id, name, kind, content, date?.let { ChatAttachment.Date(it.year, it.month, it.day, it.time) },
    storedAttachmentId, storedSizeBytes,
)
