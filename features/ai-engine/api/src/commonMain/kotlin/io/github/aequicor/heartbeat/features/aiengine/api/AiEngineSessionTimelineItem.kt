package io.github.aequicor.heartbeat.features.aiengine.api

/** A timeline entry keeps its ID and position when its content or status changes. */
public sealed interface AiEngineSessionTimelineItem {
    public val id: String

    public data class UserMessage(
        override val id: String,
        val content: List<AiEngineSessionContent>,
    ) : AiEngineSessionTimelineItem

    public data class AgentMessage(
        override val id: String,
        val content: List<AiEngineSessionContent>,
    ) : AiEngineSessionTimelineItem

    public data class ToolCall(
        override val id: String,
        val title: String,
        val status: AiEngineToolCallStatus,
        val output: List<AiEngineToolOutput> = listOf(),
    ) : AiEngineSessionTimelineItem

    public data class ContextCompaction(
        override val id: String,
        val status: AiEngineCompactionStatus,
        val summary: List<AiEngineSessionContent>? = null,
        val error: String? = null,
    ) : AiEngineSessionTimelineItem
}
