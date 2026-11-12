package io.github.aequicor.heartbeat.features.aiengine.api.session

public data class SessionState(
    val status: SessionStatus,
    val messages: MessageWindow,
)
