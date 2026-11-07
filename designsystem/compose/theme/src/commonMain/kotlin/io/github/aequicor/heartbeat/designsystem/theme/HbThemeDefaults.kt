package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes

/** Composition locals of the Design System theme. */
object HbThemeDefaults {

    /** Palette set by [HbTheme]; components read tokens through this local. */
    val LocalColors = staticCompositionLocalOf { HbColorPalettes.Light.toHbColors() }
}
