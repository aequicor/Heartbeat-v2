package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatEngineTurn(
    val text: String = "",
    val steps: List<ChatHistoryEntry.Step> = emptyList(),
    val permission: ChatTurn.Permission? = null,
    val status: Status = Status.Running,
) {

    enum class Status { Running, Completed, Stopped, Failed }
}
