@file:Suppress("MagicNumber", "TooManyFunctions")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Product vocabulary of the AI studio chrome. These glyphs belong to the studio workspace rather
 * than to the shared kit: the kit exposes the semantic icon contract, and this screen draws the
 * marks its own navigation and message actions need with the same 24-unit grid and two-unit stroke.
 */
internal enum class AiStudioGlyph(internal val draw: DrawScope.(Color) -> Unit) {
    Chat({ color -> drawChat(color) }),
    Agents({ color -> drawAgents(color) }),
    Prompts({ color -> drawPrompts(color) }),
    Plugins({ color -> drawPlugins(color) }),
    Models({ color -> drawModels(color) }),
    Folder({ color -> drawFolder(color) }),
    Sun({ color -> drawSun(color) }),
    Monitor({ color -> drawMonitor(color) }),
    Cloud({ color -> drawCloud(color) }),
    Sliders({ color -> drawSliders(color) }),
    User({ color -> drawUser(color) }),
    Panel({ color -> drawPanel(color) }),
    Copy({ color -> drawCopy(color) }),
    ThumbUp({ color -> drawThumbUp(color) }),
    Sparkles({ color -> drawSparkles(color) }),
    Settings({ color -> drawSettings(color) }),
}

/** Draws one product glyph at the size and tint of the surrounding control. */
@Composable
internal fun AiStudioIcon(
    glyph: AiStudioGlyph,
    modifier: Modifier = Modifier,
    tint: Color = HbTheme.colors.text.secondary,
) {
    Canvas(modifier) { glyph.draw(this, tint) }
}

private fun DrawScope.glyphStroke(): Stroke = Stroke(
    width = size.minDimension / 12f,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

private fun DrawScope.point(x: Float, y: Float): Offset = Offset(
    x = size.width * x / 24f,
    y = size.height * y / 24f,
)

private fun DrawScope.unit(value: Float): Float = size.minDimension * value / 24f

private fun DrawScope.drawChat(color: Color) {
    val stroke = glyphStroke()
    drawRoundRect(
        color = color,
        topLeft = point(3f, 4f),
        size = Size(unit(18f), unit(14f)),
        cornerRadius = CornerRadius(unit(4f)),
        style = stroke,
    )
    val tail = Path().apply {
        moveTo(unit(8f), unit(18f))
        lineTo(unit(8f), unit(21f))
        lineTo(unit(12f), unit(18f))
    }
    drawPath(tail, color, style = stroke)
}

private fun DrawScope.drawAgents(color: Color) {
    val stroke = glyphStroke()
    val top = Path().apply {
        moveTo(unit(12f), unit(3f))
        lineTo(unit(21f), unit(7.5f))
        lineTo(unit(12f), unit(12f))
        lineTo(unit(3f), unit(7.5f))
        close()
    }
    drawPath(top, color, style = stroke)
    drawLine(color, point(3f, 12f), point(12f, 16.5f), stroke.width, StrokeCap.Round)
    drawLine(color, point(12f, 16.5f), point(21f, 12f), stroke.width, StrokeCap.Round)
    drawLine(color, point(3f, 16.5f), point(12f, 21f), stroke.width, StrokeCap.Round)
    drawLine(color, point(12f, 21f), point(21f, 16.5f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawPrompts(color: Color) {
    val stroke = glyphStroke()
    drawRoundRect(
        color = color,
        topLeft = point(5f, 3f),
        size = Size(unit(14f), unit(18f)),
        cornerRadius = CornerRadius(unit(2f)),
        style = stroke,
    )
    listOf(8f, 12f, 16f).forEachIndexed { index, y ->
        val endX = if (index == 2) 13f else 16f
        drawLine(color, point(8f, y), point(endX, y), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.drawPlugins(color: Color) {
    val stroke = glyphStroke()
    val top = point(12f, 5f)
    val left = point(6f, 17f)
    val right = point(18f, 17f)
    drawLine(color, top, left, stroke.width, StrokeCap.Round)
    drawLine(color, top, right, stroke.width, StrokeCap.Round)
    drawLine(color, left, right, stroke.width, StrokeCap.Round)
    listOf(top, left, right).forEach { center ->
        drawCircle(color, radius = unit(2.5f), center = center, style = stroke)
    }
}

private fun DrawScope.drawModels(color: Color) {
    val stroke = glyphStroke()
    val cube = Path().apply {
        moveTo(unit(12f), unit(3f))
        lineTo(unit(20f), unit(7f))
        lineTo(unit(20f), unit(17f))
        lineTo(unit(12f), unit(21f))
        lineTo(unit(4f), unit(17f))
        lineTo(unit(4f), unit(7f))
        close()
    }
    drawPath(cube, color, style = stroke)
    drawLine(color, point(12f, 12f), point(12f, 21f), stroke.width, StrokeCap.Round)
    drawLine(color, point(12f, 12f), point(4f, 7f), stroke.width, StrokeCap.Round)
    drawLine(color, point(12f, 12f), point(20f, 7f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawFolder(color: Color) {
    val stroke = glyphStroke()
    val folder = Path().apply {
        moveTo(unit(3f), unit(7f))
        lineTo(unit(9f), unit(7f))
        lineTo(unit(11f), unit(9f))
        lineTo(unit(21f), unit(9f))
        lineTo(unit(21f), unit(19f))
        lineTo(unit(3f), unit(19f))
        close()
    }
    drawPath(folder, color, style = stroke)
}

private fun DrawScope.drawSun(color: Color) {
    val stroke = glyphStroke()
    drawCircle(color, radius = unit(4f), center = point(12f, 12f), style = stroke)
    val rays = listOf(
        12f to 2f, 12f to 4f,
        12f to 20f, 12f to 22f,
        2f to 12f, 4f to 12f,
        20f to 12f, 22f to 12f,
        4.9f to 4.9f, 6.3f to 6.3f,
        17.7f to 17.7f, 19.1f to 19.1f,
        19.1f to 4.9f, 17.7f to 6.3f,
        6.3f to 17.7f, 4.9f to 19.1f,
    )
    rays.chunked(2).forEach { (start, end) ->
        drawLine(color, point(start.first, start.second), point(end.first, end.second), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.drawMonitor(color: Color) {
    val stroke = glyphStroke()
    drawRoundRect(
        color = color,
        topLeft = point(3f, 4f),
        size = Size(unit(18f), unit(12f)),
        cornerRadius = CornerRadius(unit(2f)),
        style = stroke,
    )
    drawLine(color, point(9f, 20f), point(15f, 20f), stroke.width, StrokeCap.Round)
    drawLine(color, point(12f, 16f), point(12f, 20f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawCloud(color: Color) {
    val stroke = glyphStroke()
    val cloud = Path().apply {
        moveTo(unit(7f), unit(18f))
        cubicTo(unit(3f), unit(18f), unit(3f), unit(12f), unit(7f), unit(12f))
        cubicTo(unit(7f), unit(8f), unit(12f), unit(6f), unit(15f), unit(9f))
        cubicTo(unit(19f), unit(8f), unit(21f), unit(12f), unit(19f), unit(15f))
        cubicTo(unit(19f), unit(17f), unit(17f), unit(18f), unit(15f), unit(18f))
        close()
    }
    drawPath(cloud, color, style = stroke)
}

private fun DrawScope.drawSliders(color: Color) {
    val stroke = glyphStroke()
    drawLine(color, point(4f, 8f), point(20f, 8f), stroke.width, StrokeCap.Round)
    drawLine(color, point(4f, 16f), point(20f, 16f), stroke.width, StrokeCap.Round)
    drawCircle(color, radius = unit(2f), center = point(9f, 8f), style = stroke)
    drawCircle(color, radius = unit(2f), center = point(15f, 16f), style = stroke)
}

private fun DrawScope.drawUser(color: Color) {
    val stroke = glyphStroke()
    drawCircle(color, radius = unit(3.5f), center = point(12f, 8f), style = stroke)
    val shoulders = Path().apply {
        moveTo(unit(5f), unit(20f))
        cubicTo(unit(5f), unit(16f), unit(8f), unit(14f), unit(12f), unit(14f))
        cubicTo(unit(16f), unit(14f), unit(19f), unit(16f), unit(19f), unit(20f))
    }
    drawPath(shoulders, color, style = stroke)
}

private fun DrawScope.drawPanel(color: Color) {
    val stroke = glyphStroke()
    drawRoundRect(
        color = color,
        topLeft = point(3f, 4f),
        size = Size(unit(18f), unit(16f)),
        cornerRadius = CornerRadius(unit(2f)),
        style = stroke,
    )
    drawLine(color, point(9f, 4f), point(9f, 20f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawCopy(color: Color) {
    val stroke = glyphStroke()
    drawRoundRect(
        color = color,
        topLeft = point(8f, 8f),
        size = Size(unit(12f), unit(12f)),
        cornerRadius = CornerRadius(unit(2f)),
        style = stroke,
    )
    val back = Path().apply {
        moveTo(unit(4f), unit(16f))
        lineTo(unit(4f), unit(6f))
        cubicTo(unit(4f), unit(4f), unit(5f), unit(4f), unit(6f), unit(4f))
        lineTo(unit(16f), unit(4f))
    }
    drawPath(back, color, style = stroke)
}

private fun DrawScope.drawThumbUp(color: Color) {
    val stroke = glyphStroke()
    val thumb = Path().apply {
        moveTo(unit(4f), unit(20f))
        lineTo(unit(7f), unit(20f))
        lineTo(unit(7f), unit(10f))
        lineTo(unit(4f), unit(10f))
        close()
        moveTo(unit(9f), unit(20f))
        lineTo(unit(16.5f), unit(20f))
        cubicTo(unit(17.5f), unit(20f), unit(18.3f), unit(19.3f), unit(18.5f), unit(18.3f))
        lineTo(unit(19.8f), unit(12f))
        cubicTo(unit(20f), unit(11f), unit(19.3f), unit(10f), unit(18.3f), unit(10f))
        lineTo(unit(14f), unit(10f))
        lineTo(unit(14.7f), unit(6.8f))
        cubicTo(unit(14.9f), unit(5.9f), unit(14.3f), unit(5f), unit(13.4f), unit(5f))
        cubicTo(unit(13f), unit(5f), unit(12.6f), unit(5.2f), unit(12.4f), unit(5.5f))
        lineTo(unit(9f), unit(10f))
        close()
    }
    drawPath(thumb, color, style = stroke)
}

private fun DrawScope.drawSparkles(color: Color) {
    drawPath(
        sparklePath(centerX = 9f, centerY = 9f, radius = 5f),
        color,
    )
    drawPath(
        sparklePath(centerX = 17f, centerY = 15f, radius = 3f),
        color,
    )
}

private fun DrawScope.drawSettings(color: Color) {
    val stroke = glyphStroke()
    val center = point(12f, 12f)
    drawCircle(color, radius = unit(2.2f), center = center, style = stroke)
    drawCircle(color, radius = unit(5.5f), center = center, style = stroke)
    repeat(8) { index ->
        val angle = PI * index / 4.0
        val innerX = 12f + 5.5f * cos(angle).toFloat()
        val innerY = 12f + 5.5f * sin(angle).toFloat()
        val outerX = 12f + 8f * cos(angle).toFloat()
        val outerY = 12f + 8f * sin(angle).toFloat()
        drawLine(color, point(innerX, innerY), point(outerX, outerY), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.sparklePath(centerX: Float, centerY: Float, radius: Float): Path = Path().apply {
    val waist = radius * 0.3f
    moveTo(unit(centerX), unit(centerY - radius))
    lineTo(unit(centerX + waist), unit(centerY - waist))
    lineTo(unit(centerX + radius), unit(centerY))
    lineTo(unit(centerX + waist), unit(centerY + waist))
    lineTo(unit(centerX), unit(centerY + radius))
    lineTo(unit(centerX - waist), unit(centerY + waist))
    lineTo(unit(centerX - radius), unit(centerY))
    lineTo(unit(centerX - waist), unit(centerY - waist))
    close()
}
