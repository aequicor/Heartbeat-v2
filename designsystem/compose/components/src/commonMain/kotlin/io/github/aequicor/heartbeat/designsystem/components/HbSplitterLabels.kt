package io.github.aequicor.heartbeat.designsystem.components

/**
 * Localized names of the splitter alternatives (HB-DS-035). The dragging gesture is never the only
 * way to resize a pane, so the splitter exposes these names as accessibility actions and the owner
 * supplies the actual strings.
 */
data class HbSplitterLabels(
    val narrower: String,
    val wider: String,
    val reset: String,
)
