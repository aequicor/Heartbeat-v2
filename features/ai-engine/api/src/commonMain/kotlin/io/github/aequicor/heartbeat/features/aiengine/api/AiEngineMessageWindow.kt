package io.github.aequicor.heartbeat.features.aiengine.api

/** A partition of the session timeline in chronological order. */
public data class AiEngineMessageWindow(
    val items: List<AiEngineSessionTimelineItem>,
    val cursor: AiEngineMessageWindowCursor,
    val sync: AiEngineHistorySyncStatus,
)
