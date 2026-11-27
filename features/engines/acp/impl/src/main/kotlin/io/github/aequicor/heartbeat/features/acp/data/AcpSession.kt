package io.github.aequicor.heartbeat.features.acp.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AcpMethod.AgentMethods.V1
import com.agentclientprotocol.model.CancelNotification
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.CreateElicitationResponse
import com.agentclientprotocol.model.ElicitationAction
import com.agentclientprotocol.model.ElicitationSchema
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
import io.github.aequicor.heartbeat.features.aiengine.api.session.ContextWindowUsage
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindow
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindowCursor
import io.github.aequicor.heartbeat.features.aiengine.api.session.PendingPermission
import io.github.aequicor.heartbeat.features.aiengine.api.session.PermissionOption
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionBackgroundTurn
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSubagents
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
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
import java.time.Instant
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
    private val embeddedContext: Boolean,
    private val onStatus: (SessionStatus) -> Unit,
) : Session, SessionSubagents {

    override val supportsEmbeddedResources: Boolean get() = embeddedContext

    override val supportsImagePrompts: Boolean get() = imagePrompts

    private data class Permission(
        val request: PendingPermission,
        val childId: SessionId?,
        val answer: CompletableDeferred<RequestPermissionOutcome> = CompletableDeferred(),
    )

    private data class Questionnaire(
        val request: SessionQuestionnaire,
        val childId: SessionId?,
        val answer: CompletableDeferred<ElicitationAction> = CompletableDeferred(),
    )

    private val lock = Any()
    private val id = SessionId(initialKey.id)
    private val timeline = AcpTimeline(dshAgent)
    private val children = AcpSubagents(dshAgent)
    override val subagents = children.state

    private val permissions = linkedMapOf<String, Permission>()
    private val questionnaires = linkedMapOf<String, Questionnaire>()
    private var status: SessionStatus = SessionStatus.Unavailable
    private var history = if (replay) HistorySyncStatus.LOADING else HistorySyncStatus.LOCAL_ONLY
    private var prompt: Job? = null
    private var promptGeneration = 0L
    private var promptAccepted = false
    private var cancelling = false
    private var turn: SessionTurn? = null
    private var contextWindowUsage: ContextWindowUsage? = null
    private var terminated = false
    private val backgroundTurns = linkedMapOf<String, SessionBackgroundTurn>()
    private var backgroundId: String? = null
    private val backgroundToolTurns = mutableMapOf<String, String>()

    private val isBetweenPrompts: Boolean
        get() = status == SessionStatus.Idle || status is SessionStatus.Failed
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
                is SessionEvent.AnswerQuestionnaire -> answerQuestionnaire(event)
                SessionEvent.LoadPreviousPartition, SessionEvent.LoadNextPartition -> Unit
            }
        }
    }

    override suspend fun selectSetting(id: String, value: String): SessionSettings =
        configuration.select(id, value) {
            check(!terminated && isBetweenPrompts && permissions.isEmpty() && questionnaires.isEmpty())
        }

    fun opened() = synchronized(lock) {
        if (replay) history = HistorySyncStatus.AVAILABLE
        timeline.breakMessage()
        status = SessionStatus.Idle
        publish()
    }

    fun announceSubagent(parentId: SessionId, childId: SessionId, update: kotlinx.serialization.json.JsonObject) =
        synchronized(lock) {
            if (!terminated) children.announce(parentId.value, childId.value, update, history == HistorySyncStatus.LOADING)
        }

    fun updateSubagent(id: SessionId, update: SessionUpdate) = synchronized(lock) {
        if (!terminated) children.update(id.value, update)
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
                is SessionUpdate.UsageUpdate -> contextWindowUsage = ContextWindowUsage(update.used, update.size)
                is SessionUpdate.UserMessageChunk -> {
                    // Live user prompts are recorded when sent. User chunks supply replay history.
                    if (status != SessionStatus.Running) timeline.userChunk(update)
                }
                is SessionUpdate.ConfigOptionUpdate -> return@synchronized configuration.update(update.configOptions)
                is SessionUpdate.AgentMessageChunk -> recordBackgroundItem(
                    timeline.agentChunk(update.content, update.messageId?.value),
                )
                is SessionUpdate.AgentThoughtChunk -> recordBackgroundItem(
                    timeline.agentChunk(update.content, update.messageId?.value, reasoning = true),
                )
                is SessionUpdate.ToolCall -> updateTool(timeline.toolCall(update))
                is SessionUpdate.ToolCallUpdate -> updateTool(timeline.toolCallUpdate(update))
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

    suspend fun requestPermission(request: RequestPermissionRequest, childId: SessionId? = null): RequestPermissionResponse {
        val permission = synchronized(lock) {
            if (!acceptsRequests || request.options.isEmpty()) {
                return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
            }
            val tool = if (childId == null) {
                if (status == SessionStatus.Running) promptAccepted = true
                timeline.toolCallUpdate(request.toolCall).also {
                    if (isBetweenPrompts) backgroundTurn()
                    recordBackgroundItem(it)
                }
            } else {
                children.update(childId.value, request.toolCall)
                val item = children.state.value.first { it.id == childId.value }.items
                    .filterIsInstance<SessionTimelineItem.ToolCall>().first { it.id == "tool-${request.toolCall.toolCallId.value}" }
                item.copy(
                    id = "${childId.value}/${item.id}",
                    title = "${children.title(childId.value)}: ${item.title}",
                )
            }
            val requestId = UUID.randomUUID().toString()
            Permission(
                PendingPermission(
                    requestId,
                    tool.id,
                    tool.title,
                    request.options.map { PermissionOption(it.optionId.value, it.name, it.kind.name.lowercase()) },
                    tool,
                ),
                childId,
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

    suspend fun requestQuestionnaire(message: String, schema: ElicitationSchema, childId: SessionId? = null): CreateElicitationResponse {
        val questionnaire = synchronized(lock) {
            if (!acceptsRequests) {
                return CreateElicitationResponse(ElicitationAction.Cancel)
            }
            if (childId == null) {
                if (status == SessionStatus.Running) promptAccepted = true else backgroundTurn()
            }
            val title = if (childId == null) message else "${children.title(childId.value)}: $message"
            val requestId = UUID.randomUUID().toString()
            Questionnaire(schema.toQuestionnaire(requestId, title), childId).also {
                questionnaires[requestId] = it
                publish()
            }
        }
        return try {
            CreateElicitationResponse(questionnaire.answer.await())
        } finally {
            synchronized(lock) {
                if (questionnaires.remove(questionnaire.request.id) != null) publish()
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
        children.disconnect()
        clearRequests(includeChildren = true)
        prompt?.cancel()
        publish()
    }

    private fun rejectPrompt(event: SessionEvent.SendPrompt, failure: SessionTurn.Failure, message: String) {
        turn = event.turnId?.let { SessionTurn(it, SessionTurn.Status.Rejected, failure = failure) }
        promptAccepted = false
        status = SessionStatus.Failed(message)
        publish()
    }

    @Suppress("TooGenericExceptionCaught") // The session reports RPC and transport failures through its state.
    private fun sendPrompt(event: SessionEvent.SendPrompt) {
        if (!isBetweenPrompts || permissions.isNotEmpty() || questionnaires.isNotEmpty()) return
        val validated = event.validatedSettings
        val actual = configuration.current.securityMode
        val expected = validated?.securityMode
        val sameMode = actual?.id == expected?.id && actual?.current == expected?.current &&
            actual?.choices.isNullOrEmpty() == expected?.choices.isNullOrEmpty()
        val available = actual == null || actual.choices.isEmpty() || actual.choices.any { it.id == actual.current }
        if (validated != null && (!sameMode || !available || actual?.currentConfirmed == false)) {
            promptAccepted = false
            turn = event.turnId?.let { SessionTurn(it, SessionTurn.Status.Rejected) }
            status = SessionStatus.Failed("Режим агента изменился. Проверьте выбор перед отправкой")
            publish()
            return
        }
        val content = event.toAcpPromptContent(imagePrompts)
        when {
            !imagePrompts && content.any { it is ContentBlock.Image } -> {
                rejectPrompt(event, SessionTurn.Failure.ImageUnsupported, "Движок не поддерживает изображения в запросе")
                return
            }
            !embeddedContext && content.any { it is ContentBlock.Resource } -> {
                rejectPrompt(
                    event, SessionTurn.Failure.EmbeddedResourceUnsupported,
                    "Движок не поддерживает встроенные ресурсы в запросе",
                )
                return
            }
        }
        if (content.isEmpty()) return
        backgroundId = null
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
                        clearRequests()
                        publish()
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // Dispatch is a non-throwing boundary for transport failures.
    private fun cancel() {
        if (isBetweenPrompts && (permissions.isNotEmpty() || questionnaires.isNotEmpty())) {
            try {
                V1.SessionCancel(protocol, CancelNotification(id))
            } catch (error: Exception) {
                status = SessionStatus.Failed(error.failureReason())
            } finally {
                clearRequests()
                publish()
            }
            return
        }
        if (status == SessionStatus.Running && !cancelling) {
            cancelling = true
            try {
                V1.SessionCancel(protocol, CancelNotification(id))
            } catch (error: Exception) {
                status = SessionStatus.Failed(error.failureReason())
                turn = turn?.copy(status = promptFailureStatus())
                prompt?.cancel()
            } finally {
                clearRequests()
                publish()
            }
        }
    }

    private val acceptsRequests: Boolean
        get() = !terminated && (isBetweenPrompts || status == SessionStatus.Running && !cancelling)

    private fun backgroundTurn(): SessionBackgroundTurn {
        val id = backgroundId ?: UUID.randomUUID().toString().also { backgroundId = it }
        return backgroundTurns.getOrPut(id) { SessionBackgroundTurn(id, Instant.now().toString()) }
    }

    private fun recordBackgroundItem(item: SessionTimelineItem) {
        val known = backgroundToolTurns[item.id]?.let(backgroundTurns::get)
        if (known == null && !isBetweenPrompts) return
        val background = known ?: backgroundTurn()
        if (item is SessionTimelineItem.ToolCall) backgroundToolTurns[item.id] = background.id
        val index = background.items.indexOfFirst { it.id == item.id }
        val items = if (index == -1) background.items + item else background.items.toMutableList().apply {
            set(index, item)
        }
        backgroundTurns[background.id] = background.copy(items = items)
    }

    private fun updateTool(tool: SessionTimelineItem.ToolCall) {
        recordBackgroundItem(tool)
        fillPermissionCommand(tool)
    }

    private fun fillPermissionCommand(tool: SessionTimelineItem.ToolCall) {
        val command = tool.command ?: return
        // An approval request can arrive before the update that supplies the command.
        permissions.replaceAll { _, permission ->
            val request = permission.request
            val pendingTool = request.tool
            if (pendingTool != null && pendingTool.id == tool.id && pendingTool.command == null) {
                permission.copy(
                    request = request.copy(
                        tool = pendingTool.copy(
                            command = command,
                            workingDirectory = pendingTool.workingDirectory ?: tool.workingDirectory,
                        ),
                    ),
                )
            } else {
                permission
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

    private fun answerQuestionnaire(event: SessionEvent.AnswerQuestionnaire) {
        val questionnaire = questionnaires.remove(event.requestId) ?: return
        publish()
        questionnaire.answer.complete(event.answer.toElicitationAction())
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

    private fun clearRequests(includeChildren: Boolean = false) {
        permissions.values.filter { includeChildren || it.childId == null }.forEach {
            permissions.remove(it.request.requestId)
            it.answer.complete(RequestPermissionOutcome.Cancelled)
        }
        questionnaires.values.filter { includeChildren || it.childId == null }.forEach {
            questionnaires.remove(it.request.id)
            it.answer.complete(ElicitationAction.Cancel)
        }
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
                contextWindowUsage = contextWindowUsage,
                questionnaires = questionnaires.values.map(Questionnaire::request),
                backgroundTurns = backgroundTurns.values.toList(),
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
