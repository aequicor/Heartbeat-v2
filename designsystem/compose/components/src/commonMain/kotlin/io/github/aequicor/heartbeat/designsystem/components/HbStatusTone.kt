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
}

internal fun HbColors.containerFor(tone: HbStatusTone): Color = when (tone) {
    HbStatusTone.Neutral -> surface.subtle
    HbStatusTone.Info -> status.information.container
    HbStatusTone.Success -> status.success.container
    HbStatusTone.Warning -> status.warning.container
    HbStatusTone.Error -> status.error.container
}

internal fun HbColors.foregroundFor(tone: HbStatusTone): Color = when (tone) {
    HbStatusTone.Neutral -> text.primary
    HbStatusTone.Info -> status.information.foreground
    HbStatusTone.Success -> status.success.foreground
    HbStatusTone.Warning -> status.warning.foreground
    HbStatusTone.Error -> status.error.foreground
}
