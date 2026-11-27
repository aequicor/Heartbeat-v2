package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class ImageCropAspect(val label: String, private val width: Int = 0, private val height: Int = 0) {

    Free("Свободные"),
    Square("Квадрат · 1:1", 1, 1),
    Standard("4:3", 4, 3),
    Photo("3:2", 3, 2),
    Landscape("16:9", 16, 9),
    Portrait("9:16", 9, 16),
    ;

    private val ratio get() = if (this == Free) null else width.toFloat() / height

    fun centered(size: IntSize): IntRect {
        val marginX = if (this == Free) (size.width * (26f / 768)).roundToInt() else size.width / 20
        val marginY = if (this == Free) (size.height * (80f / 432)).roundToInt() else size.height / 20
        val width = (size.width - marginX * 2).coerceAtLeast(1)
        val height = (size.height - marginY * 2).coerceAtLeast(1)
        val aspect = ratio ?: return IntRect(marginX, marginY, marginX + width, marginY + height)
        val w = minOf(width, (height * aspect).roundToInt()).coerceAtLeast(1)
        val h = minOf(height, (w / aspect).roundToInt()).coerceAtLeast(1)
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        return IntRect(left, top, left + w, top + h)
    }

    fun fromDrag(start: Offset, end: Offset, size: IntSize): IntRect {
        val leftward = end.x < start.x
        val upward = end.y < start.y
        val maxWidth = if (leftward) start.x else size.width - start.x
        val maxHeight = if (upward) start.y else size.height - start.y
        var w = abs(end.x - start.x).coerceAtMost(maxWidth)
        var h = abs(end.y - start.y).coerceAtMost(maxHeight)
        ratio?.let { aspect ->
            w = maxOf(w, h * aspect).coerceAtMost(minOf(maxWidth, maxHeight * aspect))
            h = w / aspect
        }
        val x = if (leftward) start.x - w else start.x
        val y = if (upward) start.y - h else start.y
        val left = x.roundToInt().coerceIn(0, size.width - 1)
        val top = y.roundToInt().coerceIn(0, size.height - 1)
        return IntRect(
            left, top, (x + w).roundToInt().coerceIn(left + 1, size.width),
            (y + h).roundToInt().coerceIn(top + 1, size.height),
        )
    }

    fun resize(rect: IntRect, handle: Offset, delta: Offset, size: IntSize): IntRect {
        val x = when (handle.x) {
            0f -> rect.left.toFloat()
            1f -> rect.right.toFloat()
            else -> (rect.left + rect.right) / 2f
        }
        val y = when (handle.y) {
            0f -> rect.top.toFloat()
            1f -> rect.bottom.toFloat()
            else -> (rect.top + rect.bottom) / 2f
        }
        val edgeX = handle.x == 0.5f
        val edgeY = handle.y == 0.5f
        val drag = Offset(x, y) + delta
        val leftEdge = if (handle.x == 0f) drag.x.coerceIn(0f, rect.right - 1f) else rect.left.toFloat()
        val topEdge = if (handle.y == 0f) drag.y.coerceIn(0f, rect.bottom - 1f) else rect.top.toFloat()
        val rightEdge = if (handle.x == 1f) drag.x.coerceIn(rect.left + 1f, size.width.toFloat()) else rect.right.toFloat()
        val bottomEdge = if (handle.y == 1f) drag.y.coerceIn(rect.top + 1f, size.height.toFloat()) else rect.bottom.toFloat()
        val aspect = ratio ?: return IntRect(leftEdge.roundToInt(), topEdge.roundToInt(), rightEdge.roundToInt(), bottomEdge.roundToInt())
        if (!edgeX && !edgeY) {
            val anchor = Offset(if (handle.x == 0f) rect.right.toFloat() else rect.left.toFloat(),
                if (handle.y == 0f) rect.bottom.toFloat() else rect.top.toFloat())
            return fromDrag(anchor, Offset(if (handle.x == 0f) leftEdge else rightEdge,
                if (handle.y == 0f) topEdge else bottomEdge), size)
        }
        if (edgeY) {
            val maxHeight = minOf(y, size.height - y) * 2f
            val w = minOf(rightEdge - leftEdge, maxHeight * aspect).roundToInt().coerceAtLeast(1)
            val h = (w / aspect).roundToInt().coerceIn(1, size.height)
            val left = if (handle.x == 0f) rect.right - w else rect.left
            val top = (y - h / 2f).roundToInt().coerceIn(0, size.height - h)
            return IntRect(left, top, left + w, top + h)
        }
        val maxWidth = minOf(x, size.width - x) * 2f
        val h = minOf(bottomEdge - topEdge, maxWidth / aspect).roundToInt().coerceAtLeast(1)
        val w = (h * aspect).roundToInt().coerceIn(1, size.width)
        val left = (x - w / 2f).roundToInt().coerceIn(0, size.width - w)
        val top = if (handle.y == 0f) rect.bottom - h else rect.top
        return IntRect(left, top, left + w, top + h)
    }
}
