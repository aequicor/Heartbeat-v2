package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatSourceReference(
    val messageId: Long,
    val revisionId: Long,
    val kind: Kind,
    val blockId: String? = null,
    val toolCallId: String? = null,
    val resourceVersionId: String? = null,
    val range: Range? = null,
) {

    enum class Kind {
        Message,
        Reasoning,
        InterimResult,
        FinalResult,
        ToolCall,
        ToolResult,
        Attachment,
    }

    // UTF-16 offsets in the original block, with an exclusive end.
    data class Range(val start: Int, val endExclusive: Int) {

        init {
            require(start >= 0 && endExclusive > start)
        }

        fun overlapsOrAdjoins(other: Range): Boolean =
            start <= other.endExclusive && other.start <= endExclusive

        fun covering(other: Range): Range =
            Range(minOf(start, other.start), maxOf(endExclusive, other.endExclusive))
    }
}
