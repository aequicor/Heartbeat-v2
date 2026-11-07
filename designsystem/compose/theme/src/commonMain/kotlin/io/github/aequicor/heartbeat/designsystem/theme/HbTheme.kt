package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastSchemes
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.foundation.HbGeometries
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeScales

/**
 * Values of the active theme, read as regular properties.
 *
 * Components use this accessor instead of reading a composition local directly, so the theme
 * contract stays a small public surface: the locals themselves are private to the theme module.
 * The accessor is read-only and does not create a new theme; [HbTheme] is the only provider.
 */
object HbTheme {

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

/**
 * Wraps the content into the application theme: provides the colors behind [HbTheme.colors], the
 * type styles behind [HbTypography], the geometry behind [HbShapes] and [HbSizes], and the motion
 * preference behind [HbTheme.motion].
 *
 * [appearance] selects the semantic palette; [HbAppearance.System] follows the host where it reports
 * the preference. [contrast] applies the high-contrast mapping of the palette when it is [High], and
 * the system value resolves to the standard palette until the host reports a forced-contrast
 * setting. [textScale] is the user text preference and scales every type role, so containers grow
 * instead of clipping. [density] is the stable host default or the explicit user preference: it
 * selects the geometry and, on a touch host, the touch type scale. [motion] carries the
 * reduced-motion setting; [HbMotionPreference.System] resolves to the full vocabulary until the host
 * reports its accessibility setting.
 */
@Composable
fun HbTheme(
    appearance: HbAppearance = HbAppearance.System,
    motion: HbMotionPreference = HbMotionPreference.System,
    textScale: Float = 1f,
    density: HbDensity = HbDensity.Dense,
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
    val geometry = HbGeometries.forDensity(density)
    val typeScale = if (density == HbDensity.Touch) HbTypeScales.Touch else HbTypeScales.Desktop
    val typeStyles = remember(density, textScale) {
        typeScale.toStyles(textScale.coerceIn(MinTextScale, MaxTextScale))
    }

    CompositionLocalProvider(
        HbThemeDefaults.LocalColors provides colors,
        HbThemeDefaults.LocalMotion provides resolvedMotion,
        HbThemeDefaults.LocalTypography provides typeStyles,
        HbThemeDefaults.LocalGeometry provides geometry,
        content = content,
    )
}

private const val MinTextScale = 0.5f

private const val MaxTextScale = 3f
