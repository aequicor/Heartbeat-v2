package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes

/**
 * Wraps the content into the application theme: provides [HbThemeDefaults.LocalColors].
 *
 * [appearance] selects the semantic palette; [HbAppearance.System] follows the host where it reports
 * the preference. Material and Jewel themes build on top of the tokens when needed.
 */
@Composable
fun HbTheme(
    appearance: HbAppearance = HbAppearance.System,
    content: @Composable () -> Unit,
) {
    val isDark = when (appearance) {
        HbAppearance.System -> isSystemInDarkTheme()
        HbAppearance.Light -> false
        HbAppearance.Dark -> true
    }
    val palette = if (isDark) HbColorPalettes.Dark else HbColorPalettes.Light
    val colors = remember(palette) { palette.toHbColors() }

    CompositionLocalProvider(HbThemeDefaults.LocalColors provides colors) {
        content()
    }
}
