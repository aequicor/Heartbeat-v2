package io.github.aequicor.heartbeat.features.aiengine.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface AiEngineSession {
    public val key: AiEngineSessionKey
    public val state: StateFlow<AiEngineSessionState>

    /** Session updates delivered independently of the facade's state aggregation. */
    public val updates: Flow<AiEngineSessionUpdate>

    public fun dispatch(event: AiEngineSessionEvent)
}

public sealed interface AiEngineSessionState {
    public data object Idle : AiEngineSessionState
    public data object Running : AiEngineSessionState
    public data class AwaitingPermission(val requestId: String, val description: String) : AiEngineSessionState
    public data object Unavailable : AiEngineSessionState
    public data object Closed : AiEngineSessionState
    public data class Failed(val reason: String) : AiEngineSessionState
}

public sealed interface AiEngineSessionEvent {
    public data class SendPrompt(val text: String) : AiEngineSessionEvent
    public data object Cancel : AiEngineSessionEvent
    public data class AnswerPermission(val requestId: String, val optionId: String) : AiEngineSessionEvent
}

public sealed interface AiEngineSessionUpdate {
    public data class TextDelta(val text: String) : AiEngineSessionUpdate

    public data class ToolUpdated(
        val toolCallId: String,
        val title: String,
        val status: ToolStatus,
    ) : AiEngineSessionUpdate

    public data object Completed : AiEngineSessionUpdate
    public data class Failed(val reason: String) : AiEngineSessionUpdate

    public enum class ToolStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
    }
}

/** Identifies a session by engine ID and session ID. Metadata can change independently. */
public class AiEngineSessionKey(
    public val engine: AiEngineKey,
    public val id: String,
    public val title: String? = null,
    public val workingDirectory: String? = null,
    public val updatedAt: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is AiEngineSessionKey && engine.id == other.engine.id && id == other.id

    override fun hashCode(): Int = 31 * engine.id.hashCode() + id.hashCode()
}

public data class AiEngineSessionConfig(val workingDirectory: String? = null)

public data class AiEngineSessionSnapshot(
    val key: AiEngineSessionKey,
    val state: AiEngineSessionState,
)

public data class AiEngineSessionPage(
    val sessions: List<AiEngineSessionKey>,
    val nextCursor: String? = null,
)
