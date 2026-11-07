@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens

/** Close glyph of a dismissible surface; the accessible name comes from the action, not the glyph. */
@Composable
internal fun HbCloseGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    HbIcon(
        glyph = HbIconGlyph.Close,
        modifier = modifier.size(controlGlyphSize),
        tint = color,
        size = controlGlyphSize,
    )
}

/**
 * Chevron of an expandable trigger. The rotation follows the expanded state, and the closing glyph
 * is the same shape, so the direction is not the only signal: the surrounding control keeps its
 * accessible expanded or selected semantics.
 */
@Composable
internal fun HbChevronGlyph(
    color: Color,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = HbThemeTokens.motion == HbMotionPreference.Reduced
    val rotation by animateFloatAsState(
        targetValue = if (expanded) ChevronExpandedDegrees else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = HbMotions.Default.ordinaryTransitionMs)
        },
        label = "chevronRotation",
    )

    HbIcon(
        glyph = HbIconGlyph.ChevronDown,
        modifier = modifier.rotate(rotation),
        tint = color,
        size = controlGlyphSize,
    )
}

private val controlGlyphSize = 10.dp

private const val ChevronExpandedDegrees = 180f
