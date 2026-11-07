@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph

/** Filled and circular glyphs of the icon vocabulary. Coordinates are fractions of the icon box. */
internal fun DrawScope.drawShapeGlyph(
    glyph: HbIconGlyph,
    color: Color,
) {
    when (glyph) {
        HbIconGlyph.Search -> drawSearchGlyph(color)
        HbIconGlyph.Send -> drawSendGlyph(color)
        HbIconGlyph.Stop -> drawStopGlyph(color)
        HbIconGlyph.More -> drawMoreGlyph(color)
        HbIconGlyph.Eye -> drawEyeGlyph(color)
        HbIconGlyph.EyeOff -> drawEyeOffGlyph(color)
        else -> Unit
    }
}

private fun DrawScope.drawSearchGlyph(color: Color) {
    val stroke = size.minDimension * ShapeStrokeRatio
    drawCircle(
        color = color,
        radius = size.minDimension * 0.28f,
        center = Offset(size.width * 0.44f, size.height * 0.44f),
        style = Stroke(width = stroke),
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.64f, size.height * 0.64f),
        end = Offset(size.width * 0.84f, size.height * 0.84f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawSendGlyph(color: Color) {
    val stroke = size.minDimension * ShapeStrokeRatio
    drawLine(
        color = color,
        start = Offset(size.width * 0.18f, size.height * 0.5f),
        end = Offset(size.width * 0.8f, size.height * 0.5f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.58f, size.height * 0.28f),
        end = Offset(size.width * 0.8f, size.height * 0.5f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.58f, size.height * 0.72f),
        end = Offset(size.width * 0.8f, size.height * 0.5f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawStopGlyph(color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(size.width * 0.26f, size.height * 0.26f),
        size = Size(size.width * 0.48f, size.height * 0.48f),
    )
}

private fun DrawScope.drawMoreGlyph(color: Color) {
    listOf(0.22f, 0.5f, 0.78f).forEach { x ->
        drawCircle(
            color = color,
            radius = size.minDimension * 0.09f,
            center = Offset(size.width * x, size.height * 0.5f),
        )
    }
}

private fun DrawScope.drawEyeGlyph(color: Color) {
    drawCircle(
        color = color,
        radius = size.minDimension * 0.34f,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(width = size.minDimension * ShapeStrokeRatio),
    )
    drawCircle(
        color = color,
        radius = size.minDimension * 0.12f,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
    )
}

private fun DrawScope.drawEyeOffGlyph(color: Color) {
    drawCircle(
        color = color,
        radius = size.minDimension * 0.34f,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(width = size.minDimension * ShapeStrokeRatio),
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.2f, size.height * 0.2f),
        end = Offset(size.width * 0.8f, size.height * 0.8f),
        strokeWidth = size.minDimension * ShapeStrokeRatio,
        cap = StrokeCap.Round,
    )
}

private const val ShapeStrokeRatio = 0.14f
