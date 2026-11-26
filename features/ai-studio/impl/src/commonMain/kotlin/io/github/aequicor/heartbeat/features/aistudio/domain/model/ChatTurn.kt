package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatTurn(
    val user: ChatHistoryEntry.User,
    val reply: ChatHistoryEntry.Reply? = null,
    val permission: Permission? = null,
    val running: Boolean = true,
    val questionnaire: ChatQuestionnaire? = null,
) {

    data class Permission(
        val id: String,
        val title: String,
        val options: List<Option>,
        val paths: List<String>,
        val command: String?,
        val workingDirectory: String?,
        val requestedAccess: String?,
    )

    data class Option(val id: String, val label: String, val kind: String)
}
