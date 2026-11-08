package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.ui.graphics.Color
import io.github.aequicor.heartbeat.designsystem.foundation.HbColor as TokenColor
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorScheme
import io.github.aequicor.heartbeat.designsystem.foundation.HbStatusColors as TokenStatusColors

/** Resolves the portable color contract into Compose values (HB-DS-038). */
internal fun HbColorScheme.toHbColors(): HbColors = HbColors(
    surface = HbSurfaceColors(
        canvas = surface.canvas.toComposeColor(),
        base = surface.base.toComposeColor(),
        subtle = surface.subtle.toComposeColor(),
        raised = surface.raised.toComposeColor(),
        input = surface.input.toComposeColor(),
    ),
    text = HbTextColors(
        primary = text.primary.toComposeColor(),
        secondary = text.secondary.toComposeColor(),
        disabled = text.disabled.toComposeColor(),
    ),
    border = HbBorderColors(
        subtle = border.subtle.toComposeColor(),
        control = border.control.toComposeColor(),
    ),
    accent = HbAccentColors(
        brand = accent.brand.toComposeColor(),
        foreground = accent.foreground.toComposeColor(),
        container = accent.container.toComposeColor(),
    ),
    action = HbActionColors(
        primaryBackground = action.primaryBackground.toComposeColor(),
        primaryForeground = action.primaryForeground.toComposeColor(),
        primaryHoverBackground = action.primaryHoverBackground.toComposeColor(),
        primaryPressedBackground = action.primaryPressedBackground.toComposeColor(),
    ),
    selection = HbSelectionColors(
        background = selection.background.toComposeColor(),
        foreground = selection.foreground.toComposeColor(),
        indicator = selection.indicator.toComposeColor(),
    ),
    status = HbStatusPalette(
        success = status.success.toComposeColors(),
        warning = status.warning.toComposeColors(),
        error = status.error.toComposeColors(),
        information = status.information.toComposeColors(),
    ),
    focus = focus.toComposeColor(),
)

private fun TokenStatusColors.toComposeColors(): HbStatusColors = HbStatusColors(
    foreground = foreground.toComposeColor(),
    container = container.toComposeColor(),
)

private fun TokenColor.toComposeColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)
