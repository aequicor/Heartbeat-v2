package io.github.aequicor.heartbeat.ai.engine.acp

import com.agentclientprotocol.model.PermissionOption as AcpPermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind as AcpPermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate as AcpSessionUpdate
import com.agentclientprotocol.model.ToolCallStatus as AcpToolCallStatus
import com.agentclientprotocol.model.ContentBlock
import io.github.aequicor.heartbeat.ai.engine.api.PermissionOption
import io.github.aequicor.heartbeat.ai.engine.api.PermissionOptionKind
import io.github.aequicor.heartbeat.ai.engine.api.PermissionPrompt
import io.github.aequicor.heartbeat.ai.engine.api.SessionUpdate
import io.github.aequicor.heartbeat.ai.engine.api.ToolCallStatus
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

/** Maps an ACP update to a Heartbeat update; `null` when the update is not shown in the chat. */
internal fun AcpSessionUpdate.toEngineUpdate(): SessionUpdate? = when (this) {
    is AcpSessionUpdate.AgentMessageChunk -> content.textOrNull()
        ?.let { SessionUpdate.AgentText(it) }

    is AcpSessionUpdate.AgentThoughtChunk -> content.textOrNull()
        ?.let { SessionUpdate.AgentThought(it) }

    is AcpSessionUpdate.ToolCall -> SessionUpdate.ToolCall(
        id = toolCallId.value,
        title = title,
        status = status.toEngineStatus(),
    )

    is AcpSessionUpdate.ToolCallUpdate -> SessionUpdate.ToolCall(
        id = toolCallId.value,
        title = title.orEmpty(),
        status = status.toEngineStatus(),
    )

    else -> null
}

private fun ContentBlock.textOrNull(): String? = (this as? ContentBlock.Text)?.text

private fun AcpToolCallStatus?.toEngineStatus(): ToolCallStatus = when (this) {
    AcpToolCallStatus.PENDING -> ToolCallStatus.PENDING
    AcpToolCallStatus.IN_PROGRESS -> ToolCallStatus.IN_PROGRESS
    AcpToolCallStatus.COMPLETED -> ToolCallStatus.COMPLETED
    AcpToolCallStatus.FAILED -> ToolCallStatus.FAILED
    null -> ToolCallStatus.IN_PROGRESS
}

internal fun AcpPermissionOption.toEngineOption(): PermissionOption = PermissionOption(
    id = optionId.value,
    name = name,
    kind = kind.toEngineKind(),
)

private fun AcpPermissionOptionKind.toEngineKind(): PermissionOptionKind = when (this) {
    AcpPermissionOptionKind.ALLOW_ONCE -> PermissionOptionKind.ALLOW_ONCE
    AcpPermissionOptionKind.ALLOW_ALWAYS -> PermissionOptionKind.ALLOW_ALWAYS
    AcpPermissionOptionKind.REJECT_ONCE -> PermissionOptionKind.REJECT_ONCE
    AcpPermissionOptionKind.REJECT_ALWAYS -> PermissionOptionKind.REJECT_ALWAYS
}

/**
 * Bridges the agent permission callback to the [SessionEvent.AnswerPermission][io.github.aequicor.heartbeat.ai.engine.api.SessionEvent.AnswerPermission]
 * event.
 *
 * The ACP client answers a permission request by returning from a suspending callback, so the
 * adapter generates its own request id and parks the callback on a deferred. Every terminal path -
 * answer, cancel, disconnect, close - must complete the deferred, otherwise the agent stays blocked.
 */
internal class PermissionBridge(
    private val onPrompt: (PermissionPrompt) -> Unit,
    private val onResolved: () -> Unit,
) {
    private val pending = AtomicReference<PendingPermission?>(null)

    suspend fun await(
        title: String?,
        options: List<AcpPermissionOption>,
    ): RequestPermissionResponse {
        val requestId = "permission-${counter.incrementAndGet()}"
        val deferred = CompletableDeferred<RequestPermissionResponse>()
        val prompt = PermissionPrompt(
            requestId = requestId,
            title = title ?: DEFAULT_TITLE,
            options = options.map { it.toEngineOption() },
        )
        val request = PendingPermission(requestId, deferred)
        // A newer request replaces an unanswered one: the old agent call must not wait forever.
        pending.getAndSet(request)?.deferred?.complete(
            RequestPermissionResponse(RequestPermissionOutcome.Cancelled),
        )
        onPrompt(prompt)
        val response = deferred.await()
        if (pending.compareAndSet(request, null)) {
            onResolved()
        }
        return response
    }

    /** Completes the pending request with the chosen option; `false` when the id is stale. */
    fun answer(requestId: String, optionId: String): Boolean {
        val current = pending.get()?.takeIf { it.requestId == requestId } ?: return false
        return current.deferred.complete(
            RequestPermissionResponse(
                RequestPermissionOutcome.Selected(PermissionOptionId(optionId)),
            ),
        )
    }

    /** Completes the pending request as cancelled. Safe to call when nothing is pending. */
    fun cancel() {
        pending.getAndSet(null)?.deferred?.complete(
            RequestPermissionResponse(RequestPermissionOutcome.Cancelled),
        )
    }

    private data class PendingPermission(
        val requestId: String,
        val deferred: CompletableDeferred<RequestPermissionResponse>,
    )

    private companion object {
        private val counter = java.util.concurrent.atomic.AtomicInteger()
        private const val DEFAULT_TITLE = "Agent requests permission"
    }
}
