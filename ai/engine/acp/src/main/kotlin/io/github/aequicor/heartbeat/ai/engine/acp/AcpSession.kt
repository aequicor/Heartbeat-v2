package io.github.aequicor.heartbeat.ai.engine.acp

import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PermissionOption as AcpPermissionOption
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate as AcpSessionUpdate
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineException
import io.github.aequicor.heartbeat.ai.engine.api.AiSession
import io.github.aequicor.heartbeat.ai.engine.api.SessionEvent
import io.github.aequicor.heartbeat.ai.engine.api.SessionKey
import io.github.aequicor.heartbeat.ai.engine.api.SessionState
import io.github.aequicor.heartbeat.ai.engine.api.SessionUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Turn state, updates and the permission seam of one ACP session.
 *
 * The ACP client creates the operations object before it knows the session id for `session/new`,
 * so the operations and the public [AiSession] are separate objects: the core is created first and
 * the public session attaches to it after the agent answers.
 */
internal class SessionCore(
    private val scope: CoroutineScope,
) : ClientSessionOperations {

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableUpdates = MutableSharedFlow<SessionUpdate>(extraBufferCapacity = 128)
    val updates: SharedFlow<SessionUpdate> = mutableUpdates.asSharedFlow()

    private val permissionBridge = PermissionBridge(
        onPrompt = { prompt ->
            mutableState.value = SessionState.AwaitingPermission(prompt)
        },
        onResolved = {
            if (mutableState.value is SessionState.AwaitingPermission) {
                mutableState.value = SessionState.Running
            }
        },
    )

    private var clientSession: ClientSession? = null

    private var promptJob: Job? = null

    fun attach(session: ClientSession) {
        clientSession = session
    }

    @Suppress("TooGenericExceptionCaught") // Any agent failure becomes the Failed state of the turn.
    fun sendPrompt(text: String, key: SessionKey) {
        val session = promptableSession(key)
        mutableState.value = SessionState.Running
        promptJob = scope.launch {
            try {
                session.prompt(listOf(ContentBlock.Text(text))).collect { event ->
                    when (event) {
                        is Event.SessionUpdateEvent -> {
                            event.update.toEngineUpdate()?.let { mutableUpdates.emit(it) }
                        }

                        is Event.PromptResponseEvent -> {
                            mutableUpdates.emit(
                                SessionUpdate.Completed(
                                    event.response.stopReason.name.lowercase(),
                                ),
                            )
                            mutableState.value = SessionState.Idle
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = SessionState.Idle
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: error::class.simpleName.orEmpty()
                mutableUpdates.emit(SessionUpdate.Failed(message))
                mutableState.value = SessionState.Failed(message)
            }
        }
    }

    private fun promptableSession(key: SessionKey): ClientSession {
        val session = clientSession ?: throw AiEngineException.SessionNotFound(key)
        when (mutableState.value) {
            is SessionState.Running, is SessionState.AwaitingPermission,
            is SessionState.Unavailable, is SessionState.Closed,
            -> throw AiEngineException.SessionBusy(key)

            is SessionState.Idle, is SessionState.Failed -> Unit
        }
        return session
    }

    fun cancel() {
        permissionBridge.cancel()
        promptJob?.cancel()
        val session = clientSession ?: return
        scope.launch {
            runCatching { session.cancel() }
            mutableState.value = SessionState.Idle
        }
    }

    fun answerPermission(requestId: String, optionId: String) {
        permissionBridge.answer(requestId, optionId)
    }

    /** Moves the session to a terminal state and releases the agent callback. */
    fun shutdown(next: SessionState) {
        permissionBridge.cancel()
        promptJob?.cancel()
        promptJob = null
        mutableState.value = next
    }

    override suspend fun requestPermissions(
        toolCall: AcpSessionUpdate.ToolCallUpdate,
        permissions: List<AcpPermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse = permissionBridge.await(toolCall.title, permissions)

    /** Updates that arrive outside a prompt, for example a mode or plan change. */
    override suspend fun notify(notification: AcpSessionUpdate, _meta: JsonElement?) {
        val update = notification.toEngineUpdate() ?: return
        mutableUpdates.emit(update)
    }
}

/** Public runtime object of a conversation; delegates to [SessionCore]. */
internal class AcpSession(
    override val key: SessionKey,
    private val core: SessionCore,
) : AiSession {

    override val state: StateFlow<SessionState> = core.state

    override val updates: SharedFlow<SessionUpdate> = core.updates

    override fun dispatch(event: SessionEvent) {
        when (event) {
            is SessionEvent.SendPrompt -> core.sendPrompt(event.text, key)
            SessionEvent.Cancel -> core.cancel()
            is SessionEvent.AnswerPermission -> core.answerPermission(event.requestId, event.optionId)
        }
    }

    fun shutdown(next: SessionState) {
        core.shutdown(next)
    }
}
