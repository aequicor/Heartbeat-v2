package io.github.aequicor.heartbeat.features.aiengine.api.session

/** A timeline entry keeps its ID and position when its content or status changes. */
public sealed interface SessionTimelineItem {
    public val id: String

    public data class UserMessage(
        override val id: String,
        val content: List<SessionContent>,
    ) : SessionTimelineItem

    public data class AgentMessage(
        override val id: String,
        val content: List<SessionContent>,
    ) : SessionTimelineItem

    public data class ToolCall(
        override val id: String,
        val title: String,
        val status: ToolCallStatus,
        val output: List<ToolOutput> = listOf(),
    ) : SessionTimelineItem

    public data class ContextCompaction(
        override val id: String,
        val status: CompactionStatus,
        val summary: List<SessionContent>? = null,
        val error: String? = null,
    ) : SessionTimelineItem
}
