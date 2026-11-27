package io.github.aequicor.heartbeat.features.aistudio.domain.model

import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionQuestionnaire

internal data class BackgroundChatTurn(
    val id: String,
    val reply: ChatHistoryEntry.Reply? = null,
    val permission: ChatTurn.Permission? = null,
    val questionnaire: SessionQuestionnaire? = null,
)

internal const val BackgroundReplyIdPrefix = "background-"
