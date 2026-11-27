package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentTransferMode
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RemoteChatTurns(
    private val turns: ChatTurnInteractor,
    private val scope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher,
    private val enqueue: (suspend () -> Unit) -> Unit,
) {

    private val jobs = mutableMapOf<String, Job>()
    private val current = mutableMapOf<String, ChatTurn>()
    private val questionnaires = mutableMapOf<String, Request.Questions>()
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
                        var draftConsumed = false
                        val onUpdate: suspend (ChatTurn) -> Unit = { update ->
                            if (submission is Submission.New && !draftConsumed) {
                                draftConsumed = true
                                enqueue { submission.onCommitted() }
                            }
                            if (update.running) {
                                enqueue {
                                    current[chatId] = update
                                    val questionnaire = update.questionnaire
                                    if (questionnaire == null) questionnaires.remove(chatId)
                                    else if (questionnaires[chatId]?.id != questionnaire.id) {
                                        questionnaires[chatId] = questionnaire.toPanel()
                                    }
                                    render(update)
                                }
                            } else {
                                terminal = update
                            }
                        }
                        val shouldCancel = { cancellationRequested.isCompleted }
                        when (submission) {
                            is Submission.New -> turns.send(
                                chatId, submission.messageId, submission.prompt,
                                submission.files.map(File::forTurn), submission.expectedDraftRevision, shouldCancel, onUpdate,
                            )
                            is Submission.Retry -> turns.retry(
                                chatId, submission.messageId, submission.expectedFailedRevisionId, shouldCancel, onUpdate,
                            )
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
                    questionnaires.remove(chatId)
                    // The domain and this turn's cancellation task have finished before exposing Idle.
                    terminal?.let(render)
                    complete(outcome)
                }
            }
        }
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

    suspend fun answerQuestionnaire(chatId: String, action: AiStudioAction) {
        if (isStopping(chatId)) return
        val panel = questionnaires[chatId]?.takeUnless { it.frozen } ?: return
        when (action) {
            is AiStudioAction.Choose -> if (panel.id == action.requestId && panel.question.id == action.questionId &&
                action.option in panel.question.options
            ) {
                val options = when {
                    !panel.question.multiple -> setOf(action.option)
                    action.option in panel.answer.options -> panel.answer.options - action.option
                    else -> panel.answer.options + action.option
                }
                questionnaires[chatId] = panel.withAnswer(panel.answer.copy(options = options, skipped = false))
            }
            is AiStudioAction.EditAnswer -> if (panel.id == action.requestId &&
                panel.question.id == action.questionId && panel.question.freeText
            ) {
                questionnaires[chatId] = panel.withAnswer(panel.answer.copy(text = action.text, skipped = false))
            }
            is AiStudioAction.MoveQuestion -> if (panel.id == action.requestId) {
                val index = (panel.index + action.offset).coerceIn(panel.questions.indices)
                questionnaires[chatId] = panel.copy(index = index)
            }
            is AiStudioAction.SkipQuestion -> if (panel.id == action.requestId && !panel.question.required) {
                questionnaires[chatId] = panel.withAnswer(AiStudioScreenState.Answer(skipped = true))
                    .copy(index = (panel.index + 1).coerceAtMost(panel.questions.lastIndex))
            }
            is AiStudioAction.SubmitAnswers -> if (panel.id == action.requestId) {
                val questionnaire = current[chatId]?.questionnaire ?: return
                val values = mutableMapOf<String, QuestionnaireAnswer.Value>()
                questionnaire.fields.forEachIndexed { index, field ->
                    val answer = panel.answers[field.id]?.takeUnless { it.skipped }
                    if (!field.required && answer == null) return@forEachIndexed
                    val value = answer?.let(field::readAnswer)
                    if (value == null) {
                        questionnaires[chatId] = panel.copy(
                            index = index, error = "Проверьте ответ на этот вопрос",
                        )
                        return
                    }
                    values[field.id] = value
                }
                deliverQuestionnaire(chatId, panel, QuestionnaireAnswer.Accept(values))
            }
            is AiStudioAction.DeclineQuestionnaire -> if (panel.id == action.requestId) {
                deliverQuestionnaire(chatId, panel, QuestionnaireAnswer.Decline)
            }
            else -> Unit
        }
    }

    private suspend fun deliverQuestionnaire(chatId: String, panel: Request.Questions, answer: QuestionnaireAnswer) {
        val result = withContext(defaultDispatcher) { turns.answerQuestionnaire(chatId, panel.id, answer) }
        questionnaires[chatId] = if (result.isSuccess) panel.copy(frozen = true, error = null)
        else panel.copy(error = "Не удалось отправить ответ")
    }

    fun reply(chatId: String): Reply? {
        val turn = current[chatId]?.takeIf { it.running } ?: return null
        if (turn.user.delivery == ChatHistoryEntry.Delivery.Sending) return null
        val reply = turn.reply ?: return null
        val empty = reply.text.isEmpty() && reply.steps.isEmpty() && reply.interim == null &&
            reply.result == null && reply.artifacts.isEmpty()
        return if ((turn.permission != null || turn.questionnaire != null) && empty) null else reply.display() as? Reply
    }

    fun activity(chatId: String): AiStudioScreenState.Activity? {
        if (isStopping(chatId)) return AiStudioScreenState.Activity.Stopping
        val turn = current[chatId] ?: return if (isRunning(chatId)) AiStudioScreenState.Activity.Sending else null
        return when {
            !turn.running -> AiStudioScreenState.Activity.Idle
            turn.permission != null || turn.questionnaire != null -> AiStudioScreenState.Activity.Waiting
            turn.user.delivery == ChatHistoryEntry.Delivery.Sending -> AiStudioScreenState.Activity.Sending
            else -> AiStudioScreenState.Activity.Running
        }
    }

    fun request(chatId: String): Request? {
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
        } ?: questionnaires[chatId]
    }

    sealed interface Submission {
        data class New(
            val messageId: String,
            val prompt: String,
            val files: List<File>,
            val expectedDraftRevision: String,
            val onCommitted: () -> Unit,
        ) : Submission
        data class Retry(val messageId: String, val expectedFailedRevisionId: Long) : Submission
    }
}

internal fun File.forTurn() = ChatAttachment(
    id, name, kind, content, date?.let { ChatAttachment.Date(it.year, it.month, it.day, it.time) },
    storedAttachmentId, storedSizeBytes, image?.let { ChatAttachment.Image(it.mimeType, it.thumbnail) },
    mimeType, base64Encoded,
    source = attachmentLocation()?.let {
        AttachmentFile(id, name, it, description, mimeType, sourceSizeBytes ?: storedSizeBytes)
    },
    transferMode = if (deferredReading) AttachmentTransferMode.ResourceLink else AttachmentTransferMode.Resource,
)

internal fun File.attachmentLocation(): AttachmentLocation? =
    managedCopyPath?.let { AttachmentLocation.ManagedCopy(it, originalUri) }
        ?: originalUri?.let { AttachmentLocation.Original(it) }
