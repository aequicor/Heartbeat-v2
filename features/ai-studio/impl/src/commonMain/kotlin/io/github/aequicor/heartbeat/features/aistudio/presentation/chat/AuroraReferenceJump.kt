package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

public data class AuroraReferenceJump(
    val sequence: Long,
    val target: AuroraReferenceSelection,
    val opensSubagentChat: Boolean,
    val subagentMessages: List<AiStudioScreenState.Message>? = null,
    val subagentHistoryLoading: Boolean = false,
) {

    public constructor(sequence: Long, target: AuroraReferenceSelection) : this(sequence, target, false)
}
