package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Active palette of the application, provided by [AppTheme]. */
data class AppColors(
    val primary: Color = AppPrimary,
    val onPrimary: Color = AppOnPrimary,
    val surface: Color = AppSurface,
    val onSurface: Color = AppOnSurface,
    val background: Color = AppBackground,
    val error: Color = AppError,
)

/** Palette set by [AppTheme]; screens read tokens through this local. */
val LocalAppColors = staticCompositionLocalOf { AppColors() }

/**
 * Wraps the content into the application theme: provides [LocalAppColors].
 * Material and Jewel themes build on top of the tokens when needed.
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppColors provides AppColors()) {
        content()
    }
}
