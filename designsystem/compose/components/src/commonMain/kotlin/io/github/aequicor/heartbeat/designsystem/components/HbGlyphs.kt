@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/** Close glyph of a dismissible surface; the accessible name comes from the action, not the glyph. */
@Composable
internal fun HbCloseGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(glyphSize)) {
        val stroke = size.minDimension * GlyphStrokeRatio
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
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
    val reducedMotion = HbThemeDefaults.LocalMotion.current == HbMotionPreference.Reduced
    val rotation by animateFloatAsState(
        targetValue = if (expanded) ChevronExpandedDegrees else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = HbMotions.Default.ordinaryTransitionMs)
        },
        label = "chevronRotation",
    )

    Canvas(modifier = modifier.size(glyphSize).rotate(rotation)) {
        val stroke = size.minDimension * GlyphStrokeRatio
        drawLine(
            color = color,
            start = Offset(size.width * ChevronStartX, size.height * ChevronStartY),
            end = Offset(size.width * ChevronMiddleX, size.height * ChevronMiddleY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * ChevronMiddleX, size.height * ChevronMiddleY),
            end = Offset(size.width * ChevronEndX, size.height * ChevronStartY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private val glyphSize = 10.dp

private const val GlyphStrokeRatio = 0.16f

private const val ChevronStartX = 0.2f

private const val ChevronStartY = 0.38f

private const val ChevronMiddleX = 0.5f

private const val ChevronMiddleY = 0.66f

private const val ChevronEndX = 0.8f

private const val ChevronExpandedDegrees = 180f
