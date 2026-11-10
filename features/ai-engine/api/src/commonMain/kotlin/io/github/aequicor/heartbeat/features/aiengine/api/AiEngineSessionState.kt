package io.github.aequicor.heartbeat.features.aiengine.api

public data class AiEngineSessionState(
    val status: AiEngineSessionStatus,
    val messages: AiEngineMessageWindow,
)
