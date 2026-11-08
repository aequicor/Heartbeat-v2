package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.graphics.Color
import io.github.aequicor.heartbeat.designsystem.theme.HbColors

/**
 * Semantic tone of compact metadata and status surfaces. A tone is a meaning, not a decoration:
 * each one maps to the status roles of the color contract, and [Neutral] uses the neutral surface
 * pair so a plain label does not look like a result.
 */
enum class HbStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
    ;

    /** Container of the tone, resolved from the current color scheme. */
    fun container(colors: HbColors): Color = when (this) {
        Neutral -> colors.surface.subtle
        Info -> colors.status.information.container
        Success -> colors.status.success.container
        Warning -> colors.status.warning.container
        Error -> colors.status.error.container
    }

    /** Foreground and indicator of the tone, resolved from the current color scheme. */
    fun foreground(colors: HbColors): Color = when (this) {
        Neutral -> colors.text.primary
        Info -> colors.status.information.foreground
        Success -> colors.status.success.foreground
        Warning -> colors.status.warning.foreground
        Error -> colors.status.error.foreground
    }
}
