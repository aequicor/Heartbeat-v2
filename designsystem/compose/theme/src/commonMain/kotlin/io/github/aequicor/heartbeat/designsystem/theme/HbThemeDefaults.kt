package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes
import io.github.aequicor.heartbeat.designsystem.foundation.HbGeometries
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference

/**
 * Composition locals of the Design System theme. They are internal to the theme module: components
 * read the values through [HbTheme], [HbTypography], [HbShapes] and [HbSizes], and only
 * [HbTheme] provides them.
 */
internal object HbThemeDefaults {

    internal val LocalColors = staticCompositionLocalOf { HbColorPalettes.Light.toHbColors() }

    internal val LocalMotion = staticCompositionLocalOf { HbMotionPreference.Full }

    internal val LocalTypography = staticCompositionLocalOf { defaultTypeStyles(defaultTypeScale()) }

    internal val LocalGeometry = staticCompositionLocalOf { HbGeometries.Dense }

    internal val LocalTextScale = staticCompositionLocalOf { 1f }
}
