package io.github.aequicor.heartbeat.features.acp.data

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
import com.agentclientprotocol.protocol.JsonRpcException
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.invoke
import com.agentclientprotocol.rpc.JsonRpcErrorCode
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
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
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTurn
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(UnstableApi::class)
internal class AcpSession(
    initialKey: SessionKey,
    private val currentEngineKey: () -> AiEngineKey,
    private val protocol: Protocol,
    private val scope: CoroutineScope,
    private val replay: Boolean,
    private val dshAgent: Boolean,
    private val imagePrompts: Boolean,
    private val onStatus: (SessionStatus) -> Unit,
) : Session {

    private data class Permission(
        val request: PendingPermission,
        val answer: CompletableDeferred<RequestPermissionOutcome> = CompletableDeferred(),
    )

    private val lock = Any()
    private val id = SessionId(initialKey.id)
    private val timeline = AcpTimeline(dshAgent)
    private val permissions = linkedMapOf<String, Permission>()
    private var status: SessionStatus = SessionStatus.Unavailable
    private var history = if (replay) HistorySyncStatus.LOADING else HistorySyncStatus.LOCAL_ONLY
    private var prompt: Job? = null
    private var promptGeneration = 0L
    private var promptAccepted = false
    private var cancelling = false
    private var turn: SessionTurn? = null
    private var terminated = false
    val configuration = AcpSessionSettings(id, protocol, lock, ::publish)

    @Volatile
    private var sessionKey = initialKey

    override val key: SessionKey
        get() {
            val current = sessionKey
            val engine = currentEngineKey()
            if (current.engine == engine) return current
            return SessionKey(engine, current.id, current.title, current.workingDirectory, current.updatedAt)
        }

    private val sessionState = MutableStateFlow(snapshot)
    override val state = sessionState.asStateFlow()

    override fun dispatch(event: SessionEvent) {
        synchronized(lock) {
            when (event) {
                is SessionEvent.SendPrompt -> sendPrompt(event)
                SessionEvent.Cancel -> cancel()
                is SessionEvent.AnswerPermission -> answerPermission(event)
                SessionEvent.LoadPreviousPartition, SessionEvent.LoadNextPartition -> Unit
            }
        }
    }

    override suspend fun selectSetting(id: String, value: String): SessionSettings =
        configuration.select(id, value) {
            check(!terminated && (status == SessionStatus.Idle || status is SessionStatus.Failed))
        }

    fun opened() = synchronized(lock) {
        if (replay) history = HistorySyncStatus.AVAILABLE
        timeline.breakMessage()
        status = SessionStatus.Idle
        publish()
    }

    fun update(update: SessionUpdate) = synchronized(lock) {
        if (!terminated) {
            if (status == SessionStatus.Running && (update is SessionUpdate.UserMessageChunk ||
                    update is SessionUpdate.AgentMessageChunk || update is SessionUpdate.AgentThoughtChunk ||
                    update is SessionUpdate.ToolCall || update is SessionUpdate.ToolCallUpdate)
            ) {
                promptAccepted = true
            }
            when (update) {
                is SessionUpdate.UserMessageChunk -> {
                    // Live user prompts are recorded when sent. User chunks supply replay history.
                    if (status != SessionStatus.Running) timeline.userChunk(update)
                }
                is SessionUpdate.ConfigOptionUpdate -> return@synchronized configuration.update(update.configOptions)
                is SessionUpdate.AgentMessageChunk -> timeline.agentChunk(update.content, update.messageId?.value)
                is SessionUpdate.AgentThoughtChunk -> timeline.agentChunk(
                    update.content, update.messageId?.value, reasoning = true,
                )
                is SessionUpdate.ToolCall -> timeline.toolCall(update)
                is SessionUpdate.ToolCallUpdate -> timeline.toolCallUpdate(update)
                is SessionUpdate.SessionInfoUpdate -> {
                    val current = key
                    sessionKey = SessionKey(
                        current.engine,
                        current.id,
                        update.title ?: current.title,
                        current.workingDirectory,
                        update.updatedAt ?: current.updatedAt,
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
            promptAccepted = true
            val tool = timeline.toolCallUpdate(request.toolCall)
            val requestId = UUID.randomUUID().toString()
            Permission(
                PendingPermission(
                    requestId,
                    tool.id,
                    tool.title,
                    request.options.map { PermissionOption(it.optionId.value, it.name, it.kind.name.lowercase()) },
                    tool,
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
        val currentTurn = turn
        if (currentTurn?.status == SessionTurn.Status.Running) {
            turn = currentTurn.copy(status = promptFailureStatus())
        }
        status = finalStatus
        clearPermissions()
        prompt?.cancel()
        publish()
    }

    @Suppress("TooGenericExceptionCaught") // The session reports RPC and transport failures through its state.
    private fun sendPrompt(event: SessionEvent.SendPrompt) {
        if (status != SessionStatus.Idle && status !is SessionStatus.Failed) return
        val textBlocks = (listOf(event.text).filter(String::isNotBlank) + event.attachments).map { ContentBlock.Text(it) }
        val content = textBlocks + event.promptFiles.map { file ->
            when (file) {
                is SessionEvent.PromptFile.Text -> ContentBlock.Text(file.content)
                is SessionEvent.PromptFile.Image -> ContentBlock.Image(file.base64, file.mimeType)
            }
        }
        if (content.isEmpty()) return
        if (!imagePrompts && event.promptFiles.any { it is SessionEvent.PromptFile.Image }) {
            turn = event.turnId?.let {
                SessionTurn(it, SessionTurn.Status.Rejected, failure = SessionTurn.Failure.ImageUnsupported)
            }
            promptAccepted = false
            status = SessionStatus.Failed("Движок не поддерживает изображения в запросе")
            publish()
            return
        }
        val generation = ++promptGeneration
        status = SessionStatus.Running
        promptAccepted = false
        cancelling = false
        timeline.userPrompt(event.text)
        turn = event.turnId?.let { SessionTurn(it, SessionTurn.Status.Running) }
        publish()
        prompt = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var finalStatus: SessionStatus = SessionStatus.Idle
            var stopReason: StopReason? = null
            var failure: Exception? = null
            try {
                stopReason = V1.SessionPrompt(protocol, PromptRequest(id, content)).stopReason
            } catch (error: CancellationException) {
                finalStatus = SessionStatus.Unavailable
                throw error
            } catch (error: Exception) {
                finalStatus = SessionStatus.Failed(error.failureReason())
                failure = error
            } finally {
                synchronized(lock) {
                    if (generation == promptGeneration && status == SessionStatus.Running) {
                        val outcome = when (stopReason) {
                            StopReason.END_TURN -> SessionTurn.Status.Completed
                            StopReason.MAX_TOKENS, StopReason.MAX_TURN_REQUESTS, StopReason.REFUSAL,
                            StopReason.CANCELLED -> SessionTurn.Status.Stopped
                            null -> promptFailureStatus(failure, hasImages = content.any { it is ContentBlock.Image })
                        }
                        if (outcome == SessionTurn.Status.Stopped) timeline.cancelTools()
                        timeline.breakMessage()
                        status = finalStatus
                        val acknowledged = stopReason != null && stopReason != StopReason.CANCELLED
                        if (acknowledged || outcome == SessionTurn.Status.Failed) {
                            promptAccepted = true
                        }
                        turn = turn?.copy(status = outcome, failure = reportedFailure(failure, outcome))
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
                turn = turn?.copy(status = promptFailureStatus())
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

    private fun promptFailureStatus(failure: Exception? = null, hasImages: Boolean = false): SessionTurn.Status = when {
        promptAccepted || failure?.reportsExecutionFailure() == true -> SessionTurn.Status.Failed
        failure?.reportsRejectedPrompt() == true -> SessionTurn.Status.Rejected
        dshAgent && hasImages && failure?.reportsRejectedImage() == true -> SessionTurn.Status.Rejected
        else -> SessionTurn.Status.SendFailed
    }

    private fun reportedFailure(failure: Exception?, status: SessionTurn.Status): SessionTurn.Failure? {
        if (!dshAgent || failure !is JsonRpcException) return null
        return when {
            status == SessionTurn.Status.Rejected &&
                failure.code == JsonRpcErrorCode.INVALID_PARAMS.code &&
                failure.message.endsWith("does not declare image input") -> SessionTurn.Failure.ImageUnsupported
            // DSH's Pi-AI adapter can include HTTP status after its turn failure prefix.
            status == SessionTurn.Status.Failed &&
                failure.code == JsonRpcErrorCode.INTERNAL_ERROR.code &&
                failure.message.removePrefix("Internal error: ").startsWith("turn failed: 429: ") ->
                SessionTurn.Failure.RateLimited
            else -> null
        }
    }

    private fun clearPermissions() {
        val pending = permissions.values.toList()
        permissions.clear()
        pending.forEach { it.answer.complete(RequestPermissionOutcome.Cancelled) }
    }

    private val snapshot: SessionState
        get() {
            val pending = permissions.values.map(Permission::request)
            val effectiveStatus = if (pending.isEmpty()) status else SessionStatus.AwaitingPermission(pending)
            return SessionState(
                status = effectiveStatus,
                messages = MessageWindow(timeline.items, MessageWindowCursor(), history),
                settings = configuration.current,
                turn = turn?.copy(accepted = promptAccepted),
            )
        }

    private fun publish() {
        val current = snapshot
        sessionState.value = current
        onStatus(current.status)
    }
}

private fun Exception.reportsExecutionFailure(): Boolean {
    if (this !is JsonRpcException || code != JsonRpcErrorCode.INTERNAL_ERROR.code) return false
    // DSH reports failures after admission through these JSON-RPC error details, even without chunks.
    val detail = message.removePrefix("Internal error: ")
    return detail.startsWith("turn failed: ") || detail.startsWith("assistant output delivery failed: ")
}

private fun Exception.reportsRejectedPrompt(): Boolean =
    this is JsonRpcException && code == JsonRpcErrorCode.INTERNAL_ERROR.code &&
    message.removePrefix("Internal error: ").startsWith("prompt was not queued: ")

private fun Exception.reportsRejectedImage(): Boolean =
    this is JsonRpcException && code == JsonRpcErrorCode.INVALID_PARAMS.code
