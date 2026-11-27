package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal fun DrawScope.drawImageEdit(
    original: ImageBitmap,
    document: ImageEditDocument,
    origin: Offset = Offset.Zero,
    scale: Float = 1f,
    pending: ImageEditAnnotation? = null,
    scaleY: Float = scale,
) {
    withTransform({
        translate(origin.x, origin.y)
        scale(scale, scaleY, Offset.Zero)
    }) {
        clipRect(0f, 0f, document.size.width.toFloat(), document.size.height.toFloat()) {
            inImageEditCoordinates(document.fromOriginal) { drawImage(original) }
            document.layers.forEach { layer ->
                inImageEditCoordinates(document.fromOriginal.after(layer.toOriginal)) {
                    drawAnnotation(layer.annotation)
                }
            }
            pending?.let { drawAnnotation(it) }
        }
    }
}

internal fun ImageEditDocument.render(original: ImageBitmap, target: IntSize = size): ImageBitmap {
    val image = ImageBitmap(target.width, target.height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(target.width.toFloat(), target.height.toFloat())) {
        drawImageEdit(
            original, this@render, scale = target.width.toFloat() / size.width,
            scaleY = target.height.toFloat() / size.height,
        )
    }
    return image
}

internal fun IntSize.withLongSide(longSide: Int): IntSize {
    val scale = longSide.toDouble() / maxOf(width, height)
    return IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}
private fun DrawScope.inImageEditCoordinates(transform: ImageEditTransform, draw: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.save()
        try {
            transform.apply(canvas)
            draw()
        } finally {
            canvas.restore()
        }
    }
}

private fun DrawScope.drawAnnotation(annotation: ImageEditAnnotation) {
    when (annotation) {
        is ImageEditAnnotation.Label -> drawText(annotation.layout, topLeft = annotation.at)
        is ImageEditAnnotation.Brush -> {
            if (annotation.points.size == 1) {
                drawCircle(annotation.color, annotation.width / 2f, annotation.points.single())
            } else if (annotation.points.isNotEmpty()) {
                val path = Path().apply {
                    val first = annotation.points.first()
                    moveTo(first.x, first.y)
                    for (index in 1..annotation.points.lastIndex) {
                        val point = annotation.points[index]
                        lineTo(point.x, point.y)
                    }
                }
                drawPath(path, annotation.color, style = Stroke(annotation.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        is ImageEditAnnotation.Shape -> drawShape(annotation)
    }
}

private fun DrawScope.drawShape(shape: ImageEditAnnotation.Shape) {
    val topLeft = Offset(minOf(shape.start.x, shape.end.x), minOf(shape.start.y, shape.end.y))
    val size = Size(kotlin.math.abs(shape.end.x - shape.start.x), kotlin.math.abs(shape.end.y - shape.start.y))
    val stroke = Stroke(shape.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (shape.kind) {
        ImageEditAnnotation.Shape.Kind.Rectangle -> {
            shape.fill?.let { drawRect(it, topLeft, size) }
            drawRect(shape.color, topLeft, size, style = stroke)
        }
        ImageEditAnnotation.Shape.Kind.Ellipse -> {
            shape.fill?.let { drawOval(it, topLeft, size) }
            drawOval(shape.color, topLeft, size, style = stroke)
        }
        ImageEditAnnotation.Shape.Kind.Arrow -> {
            val direction = shape.end - shape.start
            val length = direction.getDistance()
            if (length == 0f) return
            val unit = direction / length
            val tipSize = (shape.width * 4f).coerceIn(6f, 36f).coerceAtMost(length / 2f)
            val base = shape.end - unit * tipSize
            val spread = Offset(-unit.y, unit.x) * (tipSize * 0.55f)
            drawLine(shape.color, shape.start, shape.end, shape.width, StrokeCap.Round)
            drawLine(shape.color, base + spread, shape.end, shape.width, StrokeCap.Round)
            drawLine(shape.color, base - spread, shape.end, shape.width, StrokeCap.Round)
        }
    }
}
