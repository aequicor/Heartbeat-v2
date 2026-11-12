package io.github.aequicor.heartbeat.features.aiengine.api.session

public data class SessionPage(
    val sessions: List<SessionKey>,
    val nextCursor: String? = null,
)
