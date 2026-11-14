@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
