package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastSchemes
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference

/**
 * Wraps the content into the application theme: provides [HbThemeDefaults.LocalColors],
 * [HbThemeDefaults.LocalTypography] and [HbThemeDefaults.LocalMotion].
 *
 * [appearance] selects the semantic palette; [HbAppearance.System] follows the host where it reports
 * the preference. [contrast] applies the high-contrast mapping of the palette when it is [High], and
 * the system value resolves to the standard palette until the host reports a forced-contrast
 * setting. [textScale] is the user text preference and scales every type role, so containers grow
 * instead of clipping. [motion] carries the reduced-motion setting; [HbMotionPreference.System]
 * resolves to the full vocabulary until the host reports its accessibility setting.
 */
@Composable
fun HbTheme(
    appearance: HbAppearance = HbAppearance.System,
    motion: HbMotionPreference = HbMotionPreference.System,
    textScale: Float = 1f,
    contrast: HbContrastPreference = HbContrastPreference.System,
    content: @Composable () -> Unit,
) {
    val isDark = when (appearance) {
        HbAppearance.System -> isSystemInDarkTheme()
        HbAppearance.Light -> false
        HbAppearance.Dark -> true
    }
    val basePalette = if (isDark) HbColorPalettes.Dark else HbColorPalettes.Light
    val palette = if (contrast == HbContrastPreference.High) {
        HbContrastSchemes.highContrast(basePalette)
    } else {
        basePalette
    }
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
