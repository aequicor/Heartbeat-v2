package io.github.aequicor.heartbeat.features.aiengine.api.session

/** Output observed outside a client prompt. ACP does not supply a turn boundary for it. */
public data class SessionBackgroundTurn(
    val id: String,
    val startedAt: String,
    val items: List<SessionTimelineItem> = emptyList(),
)
