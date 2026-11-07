package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes
import io.github.aequicor.heartbeat.designsystem.foundation.HbGeometries
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference

/** Composition locals of the Design System theme. */
object HbThemeDefaults {

    /** Palette set by [HbTheme]; components read tokens through this local. */
    val LocalColors = staticCompositionLocalOf { HbColorPalettes.Light.toHbColors() }

    /** Resolved motion preference set by [HbTheme]; animations read it to drop movement. */
    val LocalMotion = staticCompositionLocalOf { HbMotionPreference.Full }

    /** Type styles resolved for the active text scale; see [HbTypography]. */
    val LocalTypography = staticCompositionLocalOf { defaultTypeStyles(defaultTypeScale()) }

    /** Control geometry of the resolved density; see [HbShapes] and [HbSizes]. */
    val LocalGeometry = staticCompositionLocalOf { HbGeometries.Dense }
}
