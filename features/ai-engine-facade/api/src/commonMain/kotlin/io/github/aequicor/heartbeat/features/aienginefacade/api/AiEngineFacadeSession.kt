package io.github.aequicor.heartbeat.features.aienginefacade.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface AiEngineFacadeSession {
    public val key: AiEngineFacadeSessionKey
    public val state: StateFlow<AiEngineFacadeSessionState>

    /** Session updates delivered independently of the facade's state aggregation. */
    public val updates: Flow<AiEngineFacadeSessionUpdate>

    public fun dispatch(event: AiEngineFacadeSessionEvent)
}

public sealed interface AiEngineFacadeSessionState {
    public data object Idle : AiEngineFacadeSessionState
    public data object Running : AiEngineFacadeSessionState
    public data class AwaitingPermission(val requestId: String, val description: String) : AiEngineFacadeSessionState
    public data object Unavailable : AiEngineFacadeSessionState
    public data object Closed : AiEngineFacadeSessionState
    public data class Failed(val reason: String) : AiEngineFacadeSessionState
}

public sealed interface AiEngineFacadeSessionEvent {
    public data class SendPrompt(val text: String) : AiEngineFacadeSessionEvent
    public data object Cancel : AiEngineFacadeSessionEvent
    public data class AnswerPermission(val requestId: String, val optionId: String) : AiEngineFacadeSessionEvent
}

public sealed interface AiEngineFacadeSessionUpdate {
    public data class TextDelta(val text: String) : AiEngineFacadeSessionUpdate

    public data class ToolUpdated(
        val toolCallId: String,
        val title: String,
        val status: ToolStatus,
    ) : AiEngineFacadeSessionUpdate

    public data object Completed : AiEngineFacadeSessionUpdate
    public data class Failed(val reason: String) : AiEngineFacadeSessionUpdate

    public enum class ToolStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
    }
}

/** Identifies a session by engine ID and session ID. Metadata can change independently. */
public class AiEngineFacadeSessionKey(
    public val engine: AiEngineFacadeEngineKey,
    public val id: String,
    public val title: String? = null,
    public val workingDirectory: String? = null,
    public val updatedAt: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is AiEngineFacadeSessionKey && engine.id == other.engine.id && id == other.id

    override fun hashCode(): Int = 31 * engine.id.hashCode() + id.hashCode()
}

public data class AiEngineFacadeSessionConfig(val workingDirectory: String? = null)

public data class AiEngineFacadeSessionSnapshot(
    val key: AiEngineFacadeSessionKey,
    val state: AiEngineFacadeSessionState,
)

public data class AiEngineFacadeSessionPage(
    val sessions: List<AiEngineFacadeSessionKey>,
    val nextCursor: String? = null,
)
