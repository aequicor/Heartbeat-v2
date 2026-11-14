package io.github.aequicor.heartbeat.features.aiengineacpadapter.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AcpMethod.AgentMethods.V1
import com.agentclientprotocol.model.CancelNotification
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PromptRequest
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionRequest
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.invoke
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindow
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindowCursor
import io.github.aequicor.heartbeat.features.aiengine.api.session.PendingPermission
import io.github.aequicor.heartbeat.features.aiengine.api.session.PermissionOption
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
internal class AcpSession(
    initialKey: SessionKey,
    private val protocol: Protocol,
    private val scope: CoroutineScope,
    private val replay: Boolean,
    private val onStatus: (SessionStatus) -> Unit,
) : Session {
    private data class Permission(
        val request: PendingPermission,
        val answer: CompletableDeferred<RequestPermissionOutcome> = CompletableDeferred(),
    )

    private val lock = Any()
    private val id = SessionId(initialKey.id)
    private val timeline = AcpTimeline()
    private val permissions = linkedMapOf<String, Permission>()
    private var permissionId = 0L
    private var status: SessionStatus = SessionStatus.Unavailable
    private var history = if (replay) HistorySyncStatus.LOADING else HistorySyncStatus.LOCAL_ONLY
    private var prompt: Job? = null
    private var cancelling = false
    private var terminated = false

    @Volatile
    override var key: SessionKey = initialKey
        private set

    private val sessionState = MutableStateFlow(snapshot())
    override val state = sessionState.asStateFlow()

    override fun dispatch(event: SessionEvent) {
        synchronized(lock) {
            when (event) {
                is SessionEvent.SendPrompt -> sendPrompt(event.text)
                SessionEvent.Cancel -> cancel()
                is SessionEvent.AnswerPermission -> answerPermission(event)
                SessionEvent.LoadPreviousPartition, SessionEvent.LoadNextPartition -> Unit
            }
        }
    }

    fun opened() = synchronized(lock) {
        if (replay) history = HistorySyncStatus.AVAILABLE
        timeline.breakMessage()
        status = SessionStatus.Idle
        publish()
    }

    fun update(update: SessionUpdate) = synchronized(lock) {
        if (!terminated) {
            when (update) {
                is SessionUpdate.UserMessageChunk -> {
                    // Live user prompts are recorded when sent. User chunks supply replay history.
                    if (status != SessionStatus.Running) timeline.userChunk(update)
                }
                is SessionUpdate.AgentMessageChunk -> timeline.agentChunk(update)
                is SessionUpdate.ToolCall -> timeline.toolCall(update)
                is SessionUpdate.ToolCallUpdate -> timeline.toolCallUpdate(update)
                is SessionUpdate.SessionInfoUpdate -> {
                    key = SessionKey(
                        key.engine,
                        key.id,
                        update.title ?: key.title,
                        key.workingDirectory,
                        update.updatedAt ?: key.updatedAt,
                    )
                }
                else -> Unit
            }
            publish()
        }
    }

    suspend fun requestPermission(request: RequestPermissionRequest): RequestPermissionResponse {
        val permission = synchronized(lock) {
            if (status != SessionStatus.Running || cancelling || request.options.isEmpty()) {
                return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
            }
            val tool = timeline.toolCallUpdate(request.toolCall)
            val requestId = (++permissionId).toString()
            Permission(
                PendingPermission(
                    requestId,
                    tool.id,
                    tool.title,
                    request.options.map { PermissionOption(it.optionId.value, it.name, it.kind.name.lowercase()) },
                ),
            ).also {
                permissions[requestId] = it
                publish()
            }
        }
        return try {
            RequestPermissionResponse(permission.answer.await())
        } finally {
            synchronized(lock) {
                permissions.remove(permission.request.requestId)
                publish()
            }
        }
    }

    fun terminate(finalStatus: SessionStatus, cancelTurn: Boolean = false) = synchronized(lock) {
        if (cancelTurn) cancel()
        terminated = true
        status = finalStatus
        clearPermissions()
        prompt?.cancel()
        publish()
    }

    @Suppress("TooGenericExceptionCaught") // The session reports RPC and transport failures through its state.
    private fun sendPrompt(text: String) {
        if (text.isBlank() || (status != SessionStatus.Idle && status !is SessionStatus.Failed)) return
        status = SessionStatus.Running
        cancelling = false
        timeline.userPrompt(text)
        publish()
        prompt = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var finalStatus: SessionStatus = SessionStatus.Idle
            var cancelled = false
            try {
                val response = V1.SessionPrompt(protocol, PromptRequest(id, listOf(ContentBlock.Text(text))))
                cancelled = response.stopReason == StopReason.CANCELLED
            } catch (error: CancellationException) {
                finalStatus = SessionStatus.Unavailable
                throw error
            } catch (error: Exception) {
                finalStatus = SessionStatus.Failed(error.failureReason())
            } finally {
                synchronized(lock) {
                    if (status == SessionStatus.Running) {
                        if (cancelled) timeline.cancelTools()
                        timeline.breakMessage()
                        status = finalStatus
                        clearPermissions()
                        publish()
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // Dispatch is a non-throwing boundary for transport failures.
    private fun cancel() {
        if (status == SessionStatus.Running && !cancelling) {
            cancelling = true
            try {
                V1.SessionCancel(protocol, CancelNotification(id))
            } catch (error: Exception) {
                status = SessionStatus.Failed(error.failureReason())
                prompt?.cancel()
            } finally {
                clearPermissions()
                publish()
            }
        }
    }

    private fun answerPermission(event: SessionEvent.AnswerPermission) {
        val permission = permissions[event.requestId] ?: return
        if (permission.request.options.none { it.id == event.optionId }) return
        permissions.remove(event.requestId)
        publish()
        permission.answer.complete(RequestPermissionOutcome.Selected(PermissionOptionId(event.optionId)))
    }

    private fun clearPermissions() {
        val pending = permissions.values.toList()
        permissions.clear()
        pending.forEach { it.answer.complete(RequestPermissionOutcome.Cancelled) }
    }

    private fun snapshot(): SessionState {
        val pending = permissions.values.map(Permission::request)
        val effectiveStatus = if (pending.isEmpty()) status else SessionStatus.AwaitingPermission(pending)
        return SessionState(effectiveStatus, MessageWindow(timeline.items, MessageWindowCursor(), history))
    }

    private fun publish() {
        val current = snapshot()
        sessionState.value = current
        onStatus(current.status)
    }
}
