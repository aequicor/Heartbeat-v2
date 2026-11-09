package io.github.aequicor.heartbeat.ai.engine.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One conversation with an agent.
 *
 * A session is a runtime object: it exists while the engine holds it and becomes [SessionState.Closed]
 * after the engine closes it. Display history is not part of the session; the feature stores it.
 */
public interface AiSession {
    public val key: SessionKey

    public val state: StateFlow<SessionState>

    /**
     * Hot stream of turn updates.
     *
     * The stream is hot on purpose: a UI subscribes before [SessionEvent.SendPrompt] and receives
     * every chunk of the turn. Missed updates are not replayed and are not restored from state.
     */
    public val updates: Flow<SessionUpdate>

    public fun dispatch(event: SessionEvent)
}

/** State of one conversation. [AwaitingPermission] is a substate of a running turn. */
public sealed interface SessionState {
    public data object Idle : SessionState

    public data object Running : SessionState

    public data class AwaitingPermission(
        val prompt: PermissionPrompt,
    ) : SessionState

    /** The engine lost the connection; the session can be reopened by the engine. */
    public data object Unavailable : SessionState

    public data object Closed : SessionState

    public data class Failed(
        val reason: String,
    ) : SessionState
}

/** A permission request of the running turn and the answers the agent offers. */
public data class PermissionPrompt(
    val requestId: String,
    val title: String,
    val options: List<PermissionOption>,
)

/** One answer button of a permission request. */
public data class PermissionOption(
    val id: String,
    val name: String,
    val kind: PermissionOptionKind,
)

/** Semantics of a permission answer, independent of the agent's option ids. */
public enum class PermissionOptionKind {
    ALLOW_ONCE,
    ALLOW_ALWAYS,
    REJECT_ONCE,
    REJECT_ALWAYS,
    UNKNOWN,
}

/** Commands that change the conversation. */
public sealed interface SessionEvent {
    public data class SendPrompt(
        val text: String,
    ) : SessionEvent

    public data object Cancel : SessionEvent

    public data class AnswerPermission(
        val requestId: String,
        val optionId: String,
    ) : SessionEvent
}

/** Chunk of the running turn, delivered in order. */
public sealed interface SessionUpdate {
    public data class AgentText(
        val text: String,
    ) : SessionUpdate

    public data class AgentThought(
        val text: String,
    ) : SessionUpdate

    public data class ToolCall(
        val id: String,
        val title: String,
        val status: ToolCallStatus,
    ) : SessionUpdate

    /** The turn finished; [stopReason] is the agent-reported reason, if any. */
    public data class Completed(
        val stopReason: String? = null,
    ) : SessionUpdate

    /** The turn failed; the session moves to [SessionState.Failed] with the same message. */
    public data class Failed(
        val message: String,
    ) : SessionUpdate
}

/** Lifecycle status of a tool call reported by the agent. */
public enum class ToolCallStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}
