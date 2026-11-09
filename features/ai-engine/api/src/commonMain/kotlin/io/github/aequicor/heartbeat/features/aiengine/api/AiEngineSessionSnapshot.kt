package io.github.aequicor.heartbeat.features.aiengine.api

public data class AiEngineSessionSnapshot(
    val key: AiEngineSessionKey,
    val state: AiEngineSessionState,
)
