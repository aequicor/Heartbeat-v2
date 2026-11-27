package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

public data class AuroraReferenceSelection(
    val messageId: String,
    val kind: Kind,
    val stepId: String? = null,
    val eventId: String? = null,
    val attachmentId: String? = null,
    val range: Range? = null,
    val selectedText: String? = null,
    val revisionId: Long? = null,
) {

    public enum class Kind { Message, Reasoning, InterimResult, FinalResult, ToolCall, ToolResult, Attachment }

    public data class Range(val start: Int, val endExclusive: Int)
}
