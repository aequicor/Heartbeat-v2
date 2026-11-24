package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatEngineTurn(
    val text: String = "",
    val steps: List<ChatHistoryEntry.Step> = emptyList(),
    val permission: ChatTurn.Permission? = null,
    val status: Status = Status.Running,
    val interim: String? = null,
    val accepted: Boolean = false,
    val failure: ChatHistoryEntry.ReplyFailure? = null,
    val rejection: Rejection? = null,
    val artifacts: List<ChatAttachment> = emptyList(),
) {

    enum class Status { Running, Completed, Stopped, CancelledBeforeDispatch, Rejected, SendFailed, Failed }

    enum class Rejection { ImageUnsupported }
}
