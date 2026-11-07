package io.github.aequicor.heartbeat.designsystem.patterns

/**
 * One accepted message of the queue (HB-DS-020).
 *
 * [id] keeps the order stable across layout changes, [label] is the display text of the message
 * and [stateLabel] is the localized name of [state], because the pattern never invents product
 * strings. Editing, removal and reordering stay with the owner of the queue.
 */
data class HbQueueItem(
    val id: String,
    val label: String,
    val state: HbQueueItemState,
    val stateLabel: String,
)
