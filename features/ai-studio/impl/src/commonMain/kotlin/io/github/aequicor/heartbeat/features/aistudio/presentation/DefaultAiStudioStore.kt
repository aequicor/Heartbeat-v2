package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioState
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.seconds

internal class DefaultAiStudioStore(
    private val engines: AiEngineFacade,
    private val scope: CoroutineScope,
) : AiStudioStore {
    private val dshKey = engines.availableEngines.value.firstOrNull { it.id == "dsh" }
    private val mutableState = MutableStateFlow(AiStudioState(engineAvailable = dshKey != null))
    private val operation = Mutex()
    private var engine: AiEngine? = null
    private var session: Session? = null
    private var sessionObserver: Job? = null

    override val state = mutableState.asStateFlow()

    override fun dispatch(action: AiStudioAction) {
        scope.launch {
            when (action) {
                is AiStudioAction.EditDirectory -> editDirectory(action.value)
                is AiStudioAction.EditDraft -> mutableState.update {
                    if (it.canEditDraft) it.copy(draft = action.value) else it
                }
                AiStudioAction.CancelTurn -> session?.dispatch(SessionEvent.Cancel)
                is AiStudioAction.AnswerPermission -> session?.dispatch(
                    SessionEvent.AnswerPermission(action.requestId, action.optionId),
                )
                else -> runOperation(action)
            }
        }
    }

    private fun editDirectory(value: String) {
        mutableState.update {
            if (it.busy || it.connected) it else it.copy(directory = value, error = null)
        }
    }

    @Suppress("TooGenericExceptionCaught") // The feature translates agent and process failures into screen state.
    private suspend fun runOperation(action: AiStudioAction) {
        if (!operation.tryLock()) return
        val before = state.value
        try {
            if (!before.allows(action)) return
            mutableState.update { it.copy(busy = true, error = null) }
            when (action) {
                AiStudioAction.Connect -> connect(before.directory.trim())
                AiStudioAction.Disconnect -> disconnect()
                AiStudioAction.RefreshSessions -> listSessions()
                AiStudioAction.LoadMoreSessions -> listSessions(before.nextCursor)
                AiStudioAction.NewChat -> openChat()
                is AiStudioAction.OpenChat -> openChat(action.key)
                AiStudioAction.Send -> send(before.draft)
                else -> Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.update { it.copy(error = error.message ?: "Операция с DeepSeek Harness не выполнена") }
        } finally {
            mutableState.update { it.copy(busy = false) }
            operation.unlock()
        }
    }

    private suspend fun connect(directory: String) {
        require(directory.isNotBlank() && directory.toPath().isAbsolute) {
            "Укажите абсолютный путь к рабочему каталогу"
        }
        val active = engine ?: engines.getEngine(checkNotNull(dshKey)).also { runtime ->
            engine = runtime
            scope.launch {
                runtime.state.collect { connection ->
                    mutableState.update { it.copy(connection = connection) }
                }
            }
        }
        if (active.state.value is AiEngineState.AuthorizationRequired) disconnect()
        selectSession(null)
        mutableState.update { it.copy(directory = directory, sessions = listOf(), nextCursor = null) }
        active.dispatch(AiEngineEvent.Connect)
        val connection = withTimeoutOrNull(ConnectionTimeout) {
            active.state.first { it != AiEngineState.NotInitialized && it != AiEngineState.Connecting }
        } ?: error("DeepSeek Harness не ответил при подключении")
        mutableState.update { it.copy(connection = connection) }
        if (connection == AiEngineState.Ready) listSessions()
    }

    private suspend fun disconnect() {
        val active = checkNotNull(engine)
        active.dispatch(AiEngineEvent.Disconnect)
        withTimeoutOrNull(ConnectionTimeout) {
            active.state.first { it == AiEngineState.NotInitialized }
        } ?: error("Не удалось отключить DeepSeek Harness")
        selectSession(null)
        mutableState.update {
            it.copy(connection = AiEngineState.NotInitialized, sessions = listOf(), nextCursor = null)
        }
    }

    private suspend fun listSessions(cursor: String? = null) {
        val directory = state.value.directory
        val page = checkNotNull(engine).getListSessions(
            SessionListConfig.ByProject(projectId = directory, cursor = cursor, workingDirectory = directory),
        )
        mutableState.update {
            val sessions = if (cursor == null) page.sessions else it.sessions + page.sessions
            it.copy(sessions = sessions.distinct(), nextCursor = page.nextCursor)
        }
    }

    private suspend fun openChat(key: SessionKey? = null) {
        val opened = checkNotNull(engine).getOrCreateSession(SessionConfig(state.value.directory), key)
        selectSession(opened)
        if (key == null) mutableState.update { it.copy(sessions = (listOf(opened.key) + it.sessions).distinct()) }
    }

    private suspend fun selectSession(selected: Session?) {
        sessionObserver?.cancelAndJoin()
        session = selected
        mutableState.update { it.copy(selected = selected?.key, session = selected?.state?.value) }
        sessionObserver = selected?.let { current ->
            scope.launch {
                current.state.collect { snapshot ->
                    mutableState.update { it.copy(selected = current.key, session = snapshot) }
                }
            }
        }
    }

    private suspend fun send(draft: String) {
        val active = checkNotNull(session)
        val before = active.state.value
        check(before.status == SessionStatus.Idle || before.status is SessionStatus.Failed) {
            "Сессия сейчас не принимает сообщения"
        }
        active.dispatch(SessionEvent.SendPrompt(draft))
        val finished = active.state.first {
            it != before && it.status != SessionStatus.Running && it.status !is SessionStatus.AwaitingPermission
        }
        mutableState.update {
            it.copy(session = finished, draft = if (finished.status == SessionStatus.Idle) "" else it.draft)
        }
    }
}

private val ConnectionTimeout = 20.seconds

private fun AiStudioState.allows(action: AiStudioAction): Boolean = when (action) {
    AiStudioAction.Connect -> engineAvailable && !connected && connection != AiEngineState.Connecting
    AiStudioAction.Disconnect -> connected
    AiStudioAction.LoadMoreSessions -> canManageChats && nextCursor != null
    AiStudioAction.NewChat, is AiStudioAction.OpenChat, AiStudioAction.RefreshSessions -> canManageChats
    AiStudioAction.Send -> canSend
    else -> false
}
