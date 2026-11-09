package io.github.aequicor.heartbeat.features.aiengine.api

public data class AiEngineSessionPage(
    val sessions: List<AiEngineSessionKey>,
    val nextCursor: String? = null,
)
