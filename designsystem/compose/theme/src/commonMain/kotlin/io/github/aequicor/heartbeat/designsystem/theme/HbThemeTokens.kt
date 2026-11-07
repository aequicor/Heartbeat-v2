package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference

/**
 * Values of the active theme, read as regular properties.
 *
 * Components use this accessor instead of reading a composition local directly, so the theme
 * contract stays a small public surface: the locals themselves are private to the theme module.
 * The accessor is read-only and does not create a new theme; [HbTheme] is the only provider.
 */
object HbThemeTokens {

    /** Semantic colors of the active appearance, contrast and palette. */
    val colors: HbColors
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalColors.current

    /** Resolved motion preference; reduced motion drops movement but keeps the state change. */
    val motion: HbMotionPreference
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalMotion.current
}
