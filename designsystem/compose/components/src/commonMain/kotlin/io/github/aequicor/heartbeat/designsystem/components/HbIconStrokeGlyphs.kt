@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph

/** Line-drawn glyphs of the icon vocabulary. Coordinates are fractions of the icon box. */
internal fun DrawScope.drawStrokeGlyph(
    glyph: HbIconGlyph,
    color: Color,
) {
    val stroke = size.minDimension * StrokeRatio
    when (glyph) {
        HbIconGlyph.ChevronDown -> {
            line(color, stroke, 0.2f, 0.38f, 0.5f, 0.66f)
            line(color, stroke, 0.5f, 0.66f, 0.8f, 0.38f)
        }

        HbIconGlyph.Close -> {
            line(color, stroke, 0.2f, 0.2f, 0.8f, 0.8f)
            line(color, stroke, 0.8f, 0.2f, 0.2f, 0.8f)
        }

        HbIconGlyph.Plus -> {
            line(color, stroke, 0.5f, 0.18f, 0.5f, 0.82f)
            line(color, stroke, 0.18f, 0.5f, 0.82f, 0.5f)
        }

        HbIconGlyph.Minus -> {
            line(color, stroke, 0.18f, 0.5f, 0.82f, 0.5f)
        }

        HbIconGlyph.Check -> {
            line(color, stroke, 0.16f, 0.52f, 0.4f, 0.76f)
            line(color, stroke, 0.4f, 0.76f, 0.84f, 0.24f)
        }

        else -> Unit
    }
}

private fun DrawScope.line(
    color: Color,
    stroke: Float,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
) {
    drawLine(
        color = color,
        start = Offset(size.width * startX, size.height * startY),
        end = Offset(size.width * endX, size.height * endY),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private const val StrokeRatio = 0.14f
