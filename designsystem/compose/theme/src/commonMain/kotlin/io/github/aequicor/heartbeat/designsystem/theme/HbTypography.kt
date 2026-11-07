package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeScale
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeScales
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeStyle
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeWeights

/** Resolved type styles of the active scale and text scale. */
@Immutable
data class HbTypeStyles(
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val paneTitle: TextStyle,
    val readingBody: TextStyle,
    val uiLabel: TextStyle,
    val caption: TextStyle,
    val code: TextStyle,
    val metadata: TextStyle,
)

/**
 * Text styles of the application, resolved from the portable type scale and the active text scale.
 *
 * The properties are composable, so a screen reads the styles the theme resolved for the current
 * user preference. Containers grow with the scale instead of clipping: the sizes are computed, not
 * fixed at the call site.
 */
object HbTypography {
    val PageTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.pageTitle

    val Title: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.sectionTitle

    val PaneTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.paneTitle

    val Body: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.readingBody

    val Label: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.uiLabel

    val Caption: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.caption

    val Code: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.code

    val Metadata: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalTypography.current.metadata
}

/** Resolves the portable scale at the user text scale. */
internal fun HbTypeScale.toStyles(textScale: Float): HbTypeStyles = HbTypeStyles(
    pageTitle = pageTitle.scaled(textScale),
    sectionTitle = sectionTitle.scaled(textScale),
    paneTitle = paneTitle.scaled(textScale),
    readingBody = readingBody.scaled(textScale),
    uiLabel = uiLabel.scaled(textScale),
    caption = caption.scaled(textScale),
    code = code.scaled(textScale),
    metadata = metadata.scaled(textScale),
)

internal fun HbTypeStyle.scaled(textScale: Float): TextStyle = TextStyle(
    fontSize = (size * textScale).sp,
    lineHeight = (lineHeight * textScale).sp,
    fontWeight = weight.toFontWeight(),
)

internal fun Int.toFontWeight(): FontWeight = when (this) {
    HbTypeWeights.Medium -> FontWeight.Medium
    HbTypeWeights.SemiBold -> FontWeight.SemiBold
    else -> FontWeight.Normal
}

/** Resolved styles of a scale at the neutral text scale, used as the local default. */
internal fun defaultTypeStyles(scale: HbTypeScale): HbTypeStyles = scale.toStyles(textScale = 1f)

/** Source scale of the default theme. */
internal fun defaultTypeScale(): HbTypeScale = HbTypeScales.Desktop
