package io.github.aequicor.heartbeat.features.aiengine.api.session

public enum class HistorySyncStatus {
    LOADING,
    AVAILABLE,
    LOCAL_ONLY,
    UNAVAILABLE,
    REPLAY_CONFLICT,
}
