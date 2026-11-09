package io.github.aequicor.heartbeat.features.aiengine.api

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
