package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatCreationDraft
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatDraftState
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction.EnginePicker
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Choice
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Failure
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Selection
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class BootstrapEnginePicker(
    private val interactor: SessionBootstrapInteractor,
    private val scope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher,
    private val screen: MutableStateFlow<AiStudioScreenState>,
    private val enqueue: (suspend () -> Unit) -> Unit,
    private val onConfigurationApplied: (String, Selection) -> Unit,
) {

    private var lifetime: Job? = null
    private var requests: Job? = null
    private var lastRequest: Job? = null
    private var queue = Channel<Job>(Channel.UNLIMITED)
    private var draft: ChatCreationDraft? = null
    private var step: Step? = null
    private var dismissed = false
    private var applyAttempt: Any? = null
    private var failedApply = false
    private var optionLoad: OptionLoad? = null
    private var existingChat: String? = null
    private var existingConfiguration: Selection? = null

    val isApplyingToChat: Boolean get() = existingChat != null && applyAttempt != null

    fun start() {
        val bootstrap = screen.value.sessionBootstrap ?: return
        val project = when (val target = bootstrap.project) {
            Project.Ungrouped -> ProjectId.Ungrouped
            is Project.Existing -> ProjectId.parse(target.id)
        }
        startDraft { use -> interactor.withDraft(project, use) }
    }

    fun startExisting(chatId: String, configuration: Selection, openAt: Step) {
        startDraft(chatId, configuration, openAt) { use -> interactor.withChatConfigurationDraft(chatId, use) }
    }

    private fun startDraft(
        chatId: String? = null,
        configuration: Selection? = null,
        openAt: Step? = null,
        withDraft: suspend (suspend (ChatCreationDraft) -> Unit) -> Result<Unit>,
    ) {
        close()
        existingChat = chatId
        existingConfiguration = configuration
        dismissed = false
        step = openAt
        val commands = Channel<Job>(Channel.UNLIMITED)
        queue = commands
        publish(ChatEnginePickerState(confirmed = configuration, step = openAt, loading = openAt != null))
        lifetime = scope.launch {
            val opened = withContext(defaultDispatcher) {
                withDraft { active ->
                    enqueue {
                        if (queue === commands) {
                            draft = active
                            if (openAt != null) handle(EnginePicker.Open(openAt)) else render(active.state.value)
                        }
                    }
                    coroutineScope {
                        launch {
                            active.state.collect {
                                enqueue { if (queue === commands) render(active.state.value) }
                            }
                        }
                        for (command in commands) command.join()
                    }
                }
            }
            if (opened.isFailure) enqueue {
                if (queue === commands && existingChat != null) {
                    draft = null
                    publish(ChatEnginePickerState(confirmed = existingConfiguration, step = step, failure = Failure.LoadOptions))
                }
            }
        }
        requests = SupervisorJob(lifetime)
    }

    fun close() {
        requests?.cancel()
        lastRequest = null
        queue.close()
        lifetime?.cancel()
        draft = null
        step = null
        applyAttempt = null
        failedApply = false
        optionLoad = null
        dismissed = true
        existingChat = null
        existingConfiguration = null
        screen.update { it.copy(chatEnginePicker = null) }
    }

    fun chooseSecurityMode(value: String?) {
        if (existingChat == null && screen.value.sessionBootstrap?.picker?.securityModeBusy == false) {
            request { this.chooseSecurityMode(value) }
        }
    }

    fun handle(action: EnginePicker) {
        if (isApplyingToChat) return
        val active = draft ?: run {
            when (action) {
                EnginePicker.Dismiss -> close()
                EnginePicker.Back -> when (step) {
                    Step.Reasoning -> reopenExisting(Step.Model)
                    Step.Model -> reopenExisting(Step.Engine)
                    Step.Engine, null -> close()
                }
                EnginePicker.Retry -> reopenExisting(step ?: Step.Engine)
                else -> Unit
            }
            return
        }
        val state = active.state.value
        when (action) {
            is EnginePicker.Open -> {
                dismissed = false
                val confirmed = state.confirmed
                step = if (confirmed == null) Step.Engine else action.step
                optionLoad = confirmed?.takeIf { action.step != Step.Engine }?.let { OptionLoad(action.step, it) }
                request(replace = true) {
                    val discarded = discardChanges()
                    if (discarded.isSuccess && confirmed != null && action.step != Step.Engine) chooseEngine(confirmed.engine.id)
                    else discarded
                }
            }
            is EnginePicker.Engine -> {
                val engine = state.engines.firstOrNull { it.id == action.id } ?: return
                step = Step.Model
                dismissed = false
                optionLoad = OptionLoad(Step.Model, ChatDraftState.Selection(engine))
                request(replace = true) { chooseEngine(action.id) }
            }
            is EnginePicker.Model -> {
                val selection = state.editing ?: return
                val model = state.options?.model?.choices?.firstOrNull { it.id == action.id } ?: return
                step = Step.Reasoning
                val level = selection.level.takeIf { selection.model?.id == model.id }
                optionLoad = OptionLoad(Step.Reasoning, selection.copy(model = model, level = level), model.id)
                request(replace = true) { chooseModel(action.id) }
            }
            is EnginePicker.Reasoning -> {
                request(finishesApply = true) { applyReasoning(action.id) }
                return
            }
            EnginePicker.Apply -> {
                request(finishesApply = true) { apply() }
                return
            }
            EnginePicker.Retry -> {
                request(finishesApply = failedApply) { retry() }
                return
            }
            EnginePicker.Back -> when ((screen.value.sessionBootstrap?.picker ?: screen.value.chatEnginePicker)?.step) {
                Step.Reasoning -> {
                    val options = state.options
                    step = if (options != null && options.model == null) Step.Engine else Step.Model
                    if (step == Step.Engine) {
                        cancelRequests()
                        optionLoad = null
                    }
                }
                Step.Model -> {
                    cancelRequests()
                    step = Step.Engine
                    optionLoad = null
                }
                Step.Engine, null -> dismiss()
            }
            EnginePicker.Dismiss -> dismiss()
        }
        render(state)
    }

    private fun reopenExisting(at: Step) {
        val chatId = existingChat ?: return
        startExisting(chatId, existingConfiguration ?: return, at)
    }

    private fun dismiss() {
        if (existingChat != null) {
            close()
            return
        }
        dismissed = true
        step = null
        optionLoad = null
        request(replace = true) { discardChanges() }
    }

    private fun cancelRequests() {
        requests?.cancel()
        requests = SupervisorJob(lifetime)
        applyAttempt = null
        failedApply = false
    }

    private fun request(
        replace: Boolean = false,
        finishesApply: Boolean = false,
        operation: suspend ChatCreationDraft.() -> Result<Unit>,
    ) {
        if (replace) cancelRequests()
        val target = draft ?: return
        val parent = requests ?: return
        val attempt = if (finishesApply) Any().also { applyAttempt = it } else null
        val command = scope.launch(parent, start = CoroutineStart.LAZY) {
            val result = withContext(defaultDispatcher) { target.operation() }
            if (attempt != null) enqueue { finishApply(attempt, target, result) }
        }
        if (attempt != null) command.invokeOnCompletion { failure ->
            if (failure != null) enqueue { finishApply(attempt, target, Result.failure(failure)) }
        }
        if (queue.trySend(command).isSuccess) lastRequest = command else command.cancel()
        if (attempt != null) render(target.state.value)
    }

    suspend fun create(prompt: String, firstAttachmentName: String?): Result<SessionBootstrapResult>? {
        lastRequest?.join()
        return withContext(defaultDispatcher) { draft?.create(prompt, firstAttachmentName) }
    }

    private fun finishApply(attempt: Any, target: ChatCreationDraft, result: Result<Unit>) {
        if (applyAttempt !== attempt || draft !== target) return
        applyAttempt = null
        val state = target.state.value
        val saved = state.confirmed
        failedApply = result.isFailure && state.failed
        if (result.isSuccess && state.editing == null && saved != null) {
            val chatId = existingChat
            if (chatId != null) {
                onConfigurationApplied(chatId, saved.display())
                close()
                return
            }
            step = null
        }
        render(state)
    }

    private fun render(snapshot: ChatDraftState) {
        if (screen.value.sessionBootstrap == null && existingChat == null) return
        val target = optionLoad?.selection?.engine?.id
        if (target != null && snapshot.engines.none { it.id == target }) {
            optionLoad = null
            step = Step.Engine
        }
        val load = optionLoad
        val state = if (load != null && step == load.step && load.awaits(snapshot)) {
            snapshot.copy(editing = load.selection, options = null, busy = true, failed = false)
        } else snapshot
        val editing = state.editing
        val displayedStep = when {
            dismissed -> null
            step == Step.Engine || editing == null -> step
            state.busy || state.failed || state.options == null -> step ?: Step.Model
            state.options.model == null -> Step.Reasoning
            editing.model == null -> Step.Model
            else -> step ?: Step.Reasoning
        }
        val applying = applyAttempt != null
        val showingModelsDuringReasoningLoad = displayedStep == Step.Model && load?.step == Step.Reasoning &&
            state.options?.model != null
        val failure = when {
            applying || !state.failed || displayedStep == null || displayedStep == Step.Engine -> null
            failedApply && displayedStep == Step.Reasoning -> Failure.ApplyConfiguration
            failedApply || showingModelsDuringReasoningLoad -> null
            else -> Failure.LoadOptions
        }
        publish(ChatEnginePickerState(
            engines = state.engines.map { it.display() },
            confirmed = existingConfiguration ?: state.confirmed?.display(), step = displayedStep,
            engine = editing?.engine?.display(), models = state.options?.model?.choices?.map { it.display() },
            model = editing?.model?.display(), levels = state.options?.reasoning?.choices?.map { it.display() },
            level = editing?.level?.display(),
            loading = applying || (state.busy && !showingModelsDuringReasoningLoad) ||
                (displayedStep != null && displayedStep != Step.Engine && editing == null && !state.failed),
            failure = failure, canApply = !applying && state.canConfirm,
            securityMode = state.securityMode?.display(), securityModeBusy = state.busy, securityModeError = state.securityModeError,
        ))
    }

    private fun publish(picker: ChatEnginePickerState) {
        screen.update {
            if (existingChat != null) it.copy(chatEnginePicker = picker)
            else it.copy(sessionBootstrap = it.sessionBootstrap?.copy(picker = picker))
        }
    }

    private class OptionLoad(
        val step: Step,
        val selection: ChatDraftState.Selection,
        val modelId: String? = null,
    ) {

        fun awaits(state: ChatDraftState): Boolean {
            val editing = state.editing ?: return true
            return editing.engine.id != selection.engine.id ||
                (modelId != null && !state.failed && editing.model?.id != modelId)
        }
    }
}

private fun ChatEngineOptions.Choice.display() = Choice(id, name)

private fun ChatDraftState.Selection.display() = Selection(engine.display(), model?.display(), level?.display())
