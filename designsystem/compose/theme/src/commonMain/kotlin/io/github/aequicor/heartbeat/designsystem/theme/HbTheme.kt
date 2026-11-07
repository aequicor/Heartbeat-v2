package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Wraps the content into the application theme: provides [HbThemeDefaults.LocalColors].
 * Material and Jewel themes build on top of the tokens when needed.
 */
@Composable
fun HbTheme(
    colors: HbColors = HbColors.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(HbThemeDefaults.LocalColors provides colors) {
        content()
    }
}
