package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatDraftState
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPrompt
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PreparedChatSession
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatDraftState.Selection
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions.Choice
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectsSessionsRepository
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

internal class ChatCreationDraftImpl(
    private val projectId: ProjectId,
    private val projects: ProjectDataSource,
    private val sessions: SessionsDataSource,
    private val engines: ChatEngineDataSource,
    private val repository: ProjectsSessionsRepository,
    initial: ChatDraftState = ChatDraftState(),
    private val applyToChat: (suspend (ChatConfiguration) -> Unit)? = null,
) : ChatCreationDraft {

    private val mutex = Mutex()
    private val current = MutableStateFlow(initial)
    override val state = current.asStateFlow()
    private var candidate: PreparedChatSession? = null
    private var accepted: PreparedChatSession? = null
    private var restoreDefaults = applyToChat == null
    private val restoring = MutableStateFlow<Job?>(null)
    private var retryOperation: (suspend () -> Unit)? = null
    private var created = false

    suspend fun observeCatalog(): Unit = coroutineScope {
        val saved = if (applyToChat == null) {
            runCatching { sessions.lastConfiguration() }.rethrowCancellation().getOrNull()
        } else null
        engines.engines.collect { choices ->
            mutex.withLock {
                current.value = current.value.copy(engines = choices)
                val selected = current.value.confirmed
                if (applyToChat == null && selected != null && choices.none { it.id == selected.engine.id }) {
                    release(accepted)
                    accepted = null
                    current.value = current.value.copy(confirmed = null)
                }
                val editing = current.value.editing
                if (editing != null && choices.none { it.id == editing.engine.id }) discard()
                val previous = choices.firstOrNull { it.id == saved?.engineId }
                if (previous != null && restoreDefaults && !created) {
                    restoreDefaults = false
                    val task = launch(start = CoroutineStart.LAZY) {
                        mutex.withLock { perform { open(previous, saved, restore = true) } }
                    }
                    restoring.value = task
                    task.start()
                }
            }
        }
    }

    override suspend fun chooseEngine(id: String) = command {
        val engine = current.value.engines.first { it.id == id }
        if (candidate?.engineId != id || current.value.options == null) {
            open(engine, current.value.confirmed?.takeIf { it.engine.id == id }?.configuration(), restore = false)
        }
    }

    override suspend fun chooseModel(id: String) = command {
        val editing = requireNotNull(current.value.editing)
        val choice = requireNotNull(current.value.options?.model?.choice(id))
        val level = editing.level.takeIf { editing.model?.id == id }
        current.value = current.value.copy(editing = editing.copy(model = choice, level = level))
        configure(id, level?.id, confirm = false)
    }

    override suspend fun applyReasoning(id: String) = command {
        val editing = requireNotNull(current.value.editing)
        val choice = requireNotNull(current.value.options?.reasoning?.choice(id))
        current.value = current.value.copy(editing = editing.copy(level = choice))
        configure(editing.model?.id, choice.id, confirm = true)
    }

    override suspend fun apply() = command {
        check(current.value.selectionComplete) { "Select an available model and reasoning level" }
        val editing = requireNotNull(current.value.editing)
        configure(editing.model?.id, editing.level?.id, confirm = true)
    }

    override suspend fun discardChanges(): Result<Unit> = command { discard() }

    override suspend fun retry() = mutex.withLock { retryOperation?.let { perform(it) } ?: Result.success(Unit) }

    override suspend fun create(prompt: String, firstAttachmentName: String?): Result<SessionBootstrapResult> = mutex.withLock {
        runCatching {
            if (!ChatPrompt.canSend(prompt, hasAttachments = firstAttachmentName != null, hasActiveTurn = false)) {
                return@runCatching SessionBootstrapResult.EmptyPrompt
            }
            val title = ChatPrompt.title(prompt, firstAttachmentName)
            check(applyToChat == null && !created && candidate == null)
            val session = checkNotNull(accepted)
            val config = checkNotNull(current.value.confirmed).configuration()
            check(session.isAvailable && session.settings.model.matches(config.model) &&
                session.settings.reasoning.matches(config.reasoning))
            withContext(NonCancellable) {
                val saved = repository.createSessionInActiveProject(
                    projectId, title, Clock.System.now(), ChatConfiguration.Conversation(config, session.sessionId),
                ) ?: return@withContext SessionBootstrapResult.ProjectUnavailable
                created = true
                accepted = null // Transfer ownership only after the local transaction commits.
                SessionBootstrapResult.Created(saved.session, saved.project)
            }
        }.rethrowCancellation()
    }

    private suspend fun open(engine: Choice, previous: ChatConfiguration?, restore: Boolean) {
        release(candidate)
        candidate = null
        current.value = current.value.copy(editing = Selection(engine), options = null)
        val project = checkNotNull(projects.findById(projectId))
        check(project.status == ProjectStatus.ACTIVE)
        val directory = when (project) {
            is Project.WithDirectory -> requireNotNull(project.workingDirectory).toString()
            is Project.WithoutDirectory -> null
        }
        candidate = engines.open(projectId, engine.id, directory)
        configure(previous?.model, previous?.reasoning, restore)
    }

    private suspend fun configure(model: String?, level: String?, confirm: Boolean) {
        val session = checkNotNull(candidate)
        var options = session.settings
        val modelSetting = options.model
        val wanted = modelSetting.choice(model)
        if (modelSetting != null && wanted != null && modelSetting.current != wanted.id) {
            options = session.select(modelSetting.id, wanted.id)
            check(options.model?.current == wanted.id)
        }
        val reasoning = options.reasoning
        val selectedLevel = reasoning.choice(level).takeIf { options.model == null || wanted != null }
        if (confirm && reasoning != null && selectedLevel != null && reasoning.current != selectedLevel.id) {
            options = session.select(reasoning.id, selectedLevel.id)
            check(options.reasoning?.current == selectedLevel.id)
        }
        val selection = requireNotNull(current.value.editing).copy(
            model = options.model.choice(model), level = options.reasoning.choice(selectedLevel?.id),
        )
        val next = current.value.copy(editing = selection, options = options, busy = false, failed = false)
        if (confirm && next.canConfirm && options.model.matches(selection.model?.id) &&
            options.reasoning.matches(selection.level?.id)) {
            applyToChat?.invoke(selection.configuration())
            release(accepted)
            accepted = session
            candidate = null
            current.value = ChatDraftState(next.engines, confirmed = selection)
        } else current.value = next
    }

    private suspend fun command(operation: suspend () -> Unit): Result<Unit> {
        restoring.value?.cancelAndJoin()
        return mutex.withLock {
            restoreDefaults = false
            // A catalog emission may have scheduled restoration just before this command acquired the lock.
            restoring.value?.cancel()
            perform(operation)
        }
    }

    private suspend fun perform(operation: suspend () -> Unit): Result<Unit> {
        retryOperation = null
        current.value = current.value.copy(busy = true, failed = false)
        return try {
            runCatching { operation() }.rethrowCancellation().onFailure {
                retryOperation = operation
                current.value = current.value.copy(failed = true)
            }
        } finally {
            current.value = current.value.copy(busy = false)
        }
    }

    private suspend fun discard() {
        release(candidate)
        candidate = null
        retryOperation = null
        current.value = ChatDraftState(current.value.engines, confirmed = current.value.confirmed)
    }

    suspend fun close() = mutex.withLock {
        try { release(candidate) } finally { release(accepted) }
    }

    private suspend fun release(session: PreparedChatSession?) {
        if (session != null) withContext(NonCancellable) { session.close() }
    }
}

private fun ChatEngineOptions.Selector?.choice(id: String?): Choice? = this?.choices?.firstOrNull { it.id == id }

private fun ChatEngineOptions.Selector?.matches(value: String?) =
    if (this == null) value == null else current == value && choice(value) != null
