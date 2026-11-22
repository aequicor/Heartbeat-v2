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
) {

    private var lifetime: Job? = null
    private var requests: Job? = null
    private var lastRequest: Job? = null
    private var queue = Channel<Job>(Channel.UNLIMITED)
    private var draft: ChatCreationDraft? = null
    private var step: Step? = null
    private var dismissed = false
    private var applying = false

    fun start() {
        close()
        val bootstrap = screen.value.sessionBootstrap ?: return
        val project = when (val target = bootstrap.project) {
            Project.Ungrouped -> ProjectId.Ungrouped
            is Project.Existing -> ProjectId.parse(target.id)
        }
        dismissed = false
        val commands = Channel<Job>(Channel.UNLIMITED)
        queue = commands
        publish(ChatEnginePickerState())
        lifetime = scope.launch {
            withContext(defaultDispatcher) {
                interactor.withDraft(project) { active ->
                    enqueue { if (queue === commands) draft = active }
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
        applying = false
        dismissed = true
    }

    fun handle(action: EnginePicker) {
        val active = draft ?: return
        val state = active.state.value
        when (action) {
            is EnginePicker.Open -> {
                dismissed = false
                step = if (state.confirmed == null) Step.Engine else action.step
                val confirmed = state.confirmed
                if (confirmed != null && step != Step.Engine) request(replace = true) { chooseEngine(confirmed.engine.id) }
                else request(replace = true) { discardChanges() }
            }
            is EnginePicker.Engine -> {
                step = Step.Model
                dismissed = false
                request(replace = true) { chooseEngine(action.id) }
            }
            is EnginePicker.Model -> {
                step = Step.Reasoning
                request(replace = true) { chooseModel(action.id) }
            }
            is EnginePicker.Reasoning -> request { chooseReasoning(action.id) }
            EnginePicker.Apply -> {
                applying = true
                request { apply() }
            }
            EnginePicker.Retry -> request { retry() }
            EnginePicker.Back -> when (screen.value.sessionBootstrap?.picker?.step) {
                Step.Reasoning -> {
                    cancelRequests()
                    step = if (state.options?.model == null) Step.Engine else Step.Model
                }
                Step.Model -> {
                    cancelRequests()
                    step = Step.Engine
                }
                Step.Engine, null -> dismiss()
            }
            EnginePicker.Dismiss -> dismiss()
        }
        render(state)
    }

    private fun dismiss() {
        dismissed = true
        step = null
        request(replace = true) { discardChanges() }
    }

    private fun cancelRequests() {
        requests?.cancel()
        requests = SupervisorJob(lifetime)
        applying = false
    }

    private fun request(replace: Boolean = false, operation: suspend ChatCreationDraft.() -> Result<Unit>) {
        if (replace) cancelRequests()
        val target = draft ?: return
        val parent = requests ?: return
        val command = scope.launch(parent, start = CoroutineStart.LAZY) {
            withContext(defaultDispatcher) { target.operation() }
        }
        if (queue.trySend(command).isSuccess) lastRequest = command else command.cancel()
    }

    suspend fun create(prompt: String): Result<SessionBootstrapResult>? {
        lastRequest?.join()
        return withContext(defaultDispatcher) { draft?.create(prompt) }
    }

    private fun render(state: ChatDraftState) {
        if (screen.value.sessionBootstrap == null) return
        val editing = state.editing
        if (editing == null && state.confirmed != null && applying && !state.busy) {
            step = null
            applying = false
        }
        val displayedStep = when {
            dismissed -> null
            step == Step.Engine || editing == null -> step
            state.busy || state.failed || state.options == null -> step ?: Step.Model
            state.options.model == null -> Step.Reasoning
            editing.model == null -> Step.Model
            else -> step ?: Step.Reasoning
        }
        publish(ChatEnginePickerState(
            engines = state.engines.map { it.display() }, confirmed = state.confirmed?.display(), step = displayedStep,
            engine = editing?.engine?.display(), models = state.options?.model?.choices?.map { it.display() },
            model = editing?.model?.display(), levels = state.options?.reasoning?.choices?.map { it.display() },
            level = editing?.level?.display(), loading = state.busy, failed = state.failed, canApply = state.canConfirm,
        ))
    }

    private fun publish(picker: ChatEnginePickerState) {
        screen.update { it.copy(sessionBootstrap = it.sessionBootstrap?.copy(picker = picker)) }
    }
}

private fun ChatEngineOptions.Choice.display() = Choice(id, name)

private fun ChatDraftState.Selection.display() = Selection(engine.display(), model?.display(), level?.display())
