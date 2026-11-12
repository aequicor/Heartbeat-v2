package io.github.aequicor.heartbeat.features.aiengine.api.session

/** A partition of the session timeline in chronological order. */
public data class MessageWindow(
    val items: List<SessionTimelineItem>,
    val cursor: MessageWindowCursor,
    val sync: HistorySyncStatus,
)
