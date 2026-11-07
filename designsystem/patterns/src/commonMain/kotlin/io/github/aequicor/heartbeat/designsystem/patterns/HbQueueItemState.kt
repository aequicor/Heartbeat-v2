package io.github.aequicor.heartbeat.designsystem.patterns

/** Delivery state of a queued message (HB-DS-020). */
enum class HbQueueItemState {
    Accepted,
    Queued,
    Sending,
    Failed,
}
