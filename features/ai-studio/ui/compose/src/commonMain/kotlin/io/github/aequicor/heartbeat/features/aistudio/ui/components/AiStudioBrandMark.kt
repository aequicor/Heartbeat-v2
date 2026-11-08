@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

/** Single heartbeat mark of the product: the line and the pulse that names the studio. */
@Composable
internal fun AiStudioBrandMark(
    modifier: Modifier = Modifier,
    color: Color = HbTheme.colors.accent.brand,
) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val middle = height / 2f
        val pulse = Path().apply {
            moveTo(0f, middle)
            lineTo(width * 0.20f, middle)
            lineTo(width * 0.28f, height * 0.12f)
            lineTo(width * 0.39f, height * 0.92f)
            lineTo(width * 0.49f, height * 0.36f)
            lineTo(width * 0.57f, middle)
            lineTo(width, middle)
        }
        drawPath(
            path = pulse,
            color = color,
            style = Stroke(
                width = height * 0.11f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Divider of the model introduction. A quiet line carries one small pulse, so the split stays
 * visible without turning the heading into a decorated banner.
 */
@Composable
internal fun AiStudioPulseDivider(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Canvas(modifier.fillMaxWidth().height(HbSizes.minimumTarget)) {
        val middle = size.height / 2f
        drawLine(
            color = colors.border.subtle,
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = size.height * 0.04f,
        )
        val pulse = Path().apply {
            moveTo(size.width * 0.40f, middle)
            lineTo(size.width * 0.45f, middle)
            lineTo(size.width * 0.47f, middle - size.height * 0.16f)
            lineTo(size.width * 0.50f, middle + size.height * 0.22f)
            lineTo(size.width * 0.53f, middle - size.height * 0.30f)
            lineTo(size.width * 0.56f, middle)
            lineTo(size.width * 0.60f, middle)
        }
        drawPath(
            path = pulse,
            color = colors.accent.brand,
            style = Stroke(
                width = size.height * 0.06f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
