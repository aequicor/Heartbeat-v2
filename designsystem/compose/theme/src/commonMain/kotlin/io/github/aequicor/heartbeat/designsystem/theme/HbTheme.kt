package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference

/**
 * Wraps the content into the application theme: provides [HbThemeDefaults.LocalColors],
 * [HbThemeDefaults.LocalTypography] and [HbThemeDefaults.LocalMotion].
 *
 * [appearance] selects the semantic palette; [HbAppearance.System] follows the host where it reports
 * the preference. [textScale] is the user text preference and scales every type role, so containers
 * grow instead of clipping. [motion] carries the reduced-motion setting;
 * [HbMotionPreference.System] resolves to the full vocabulary until the host reports its
 * accessibility setting. Material and Jewel themes build on top of the tokens when needed.
 */
@Composable
fun HbTheme(
    appearance: HbAppearance = HbAppearance.System,
    motion: HbMotionPreference = HbMotionPreference.System,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val isDark = when (appearance) {
        HbAppearance.System -> isSystemInDarkTheme()
        HbAppearance.Light -> false
        HbAppearance.Dark -> true
    }
    val palette = if (isDark) HbColorPalettes.Dark else HbColorPalettes.Light
    val colors = remember(palette) { palette.toHbColors() }
    val resolvedMotion = when (motion) {
        HbMotionPreference.System -> HbMotionPreference.Full
        HbMotionPreference.Reduced, HbMotionPreference.Full -> motion
    }
    val typeStyles = remember(textScale) {
        defaultTypeScale().toStyles(textScale.coerceIn(MinTextScale, MaxTextScale))
    }

    CompositionLocalProvider(
        HbThemeDefaults.LocalColors provides colors,
        HbThemeDefaults.LocalMotion provides resolvedMotion,
        HbThemeDefaults.LocalTypography provides typeStyles,
        content = content,
    )
}

private const val MinTextScale = 0.5f

private const val MaxTextScale = 3f
