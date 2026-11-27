package io.github.aequicor.heartbeat.features.aiengine.api.session

public data class SessionState(
    val status: SessionStatus,
    val messages: MessageWindow,
    val settings: SessionSettings = SessionSettings(),
    val turn: SessionTurn? = null,
    val contextWindowUsage: ContextWindowUsage? = null,
    val questionnaires: List<SessionQuestionnaire> = emptyList(),
    val backgroundTurns: List<SessionBackgroundTurn> = emptyList(),
)
