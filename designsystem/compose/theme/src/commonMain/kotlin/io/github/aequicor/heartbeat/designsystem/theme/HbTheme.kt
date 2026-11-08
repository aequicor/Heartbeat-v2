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

    /**
     * Resolved user text scale. A component that draws a fixed geometry around scalable text, such
     * as an avatar or a badge, multiplies that geometry by this value so the text never outgrows its
     * container.
     */
    val textScale: Float
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTextScale.current
}

/**
 * Wraps the content into the application theme: provides the colors behind [HbTheme.colors], the
 * type styles behind [HbTypography], the geometry behind [HbShapes] and [HbSizes], and the motion
 * preference behind [HbTheme.motion].
 *
 * [appearance] selects the semantic palette; [HbAppearance.System] follows the host where it reports
 * the preference. [contrast] applies the high-contrast mapping of the palette when it is [High];
 * [HbContrastPreference.System] resolves to [systemContrast], which the host reports from its
 * forced-contrast setting. [textScale] is the user text preference and scales every type role, so
 * containers grow instead of clipping. [density] is the stable host default or the explicit user
 * preference: it selects the geometry and, on a touch host, the touch type scale. [motion] carries
 * the reduced-motion setting; [HbMotionPreference.System] resolves to [systemMotion], which the host
 * reports from its accessibility setting.
 *
 * [systemMotion] and [systemContrast] are the explicit contract for the host to pass resolved system
 * preferences. They must be concrete values; the [System] marker is never used as a resolved value.
 */
@Composable
fun HbTheme(
    appearance: HbAppearance = HbAppearance.System,
    motion: HbMotionPreference = HbMotionPreference.System,
    textScale: Float = 1f,
    density: HbDensity = HbDensity.Dense,
    contrast: HbContrastPreference = HbContrastPreference.System,
    systemMotion: HbMotionPreference = HbMotionPreference.Full,
    systemContrast: HbContrastPreference = HbContrastPreference.Standard,
    content: @Composable () -> Unit,
) {
    val isDark = when (appearance) {
        HbAppearance.System -> isSystemInDarkTheme()
        HbAppearance.Light -> false
        HbAppearance.Dark -> true
    }
    val basePalette = if (isDark) HbColorPalettes.Dark else HbColorPalettes.Light
    val resolvedContrast = contrast.resolve(systemContrast)
    val palette = if (resolvedContrast == HbContrastPreference.High) {
        HbContrastSchemes.highContrast(basePalette)
    } else {
        basePalette
    }
    val colors = remember(palette) { palette.toHbColors() }
    val resolvedMotion = motion.resolve(systemMotion)
    val geometry = HbGeometries.forDensity(density)
    val typeScale = if (density == HbDensity.Touch) HbTypeScales.Touch else HbTypeScales.Desktop
    val resolvedTextScale = textScale.coerceIn(MinTextScale, MaxTextScale)
    val typeStyles = remember(density, resolvedTextScale) {
        typeScale.toStyles(resolvedTextScale)
    }

    CompositionLocalProvider(
        HbThemeDefaults.LocalColors provides colors,
        HbThemeDefaults.LocalMotion provides resolvedMotion,
        HbThemeDefaults.LocalTypography provides typeStyles,
        HbThemeDefaults.LocalGeometry provides geometry,
        HbThemeDefaults.LocalTextScale provides resolvedTextScale,
        content = content,
    )
}

private const val MinTextScale = 0.5f

private const val MaxTextScale = 3f
