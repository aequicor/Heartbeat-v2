package io.github.aequicor.heartbeat.features.aiengine.api

public enum class AiEngineHistorySyncStatus {
    LOADING,
    AVAILABLE,
    LOCAL_ONLY,
    UNAVAILABLE,
    REPLAY_CONFLICT,
}
