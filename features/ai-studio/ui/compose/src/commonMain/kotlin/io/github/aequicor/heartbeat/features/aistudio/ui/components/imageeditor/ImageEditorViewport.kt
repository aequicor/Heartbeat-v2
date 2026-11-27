package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

internal data class ImageEditorViewport(val zoom: Float = 1f, val pan: Offset = Offset.Zero) {

    fun bounds(canvas: IntSize, image: IntSize, density: Float): Rect {
        val fitted = minOf(density, canvas.width.toFloat() / image.width, canvas.height.toFloat() / image.height)
        val scale = fitted * zoom
        val size = Size(image.width * scale, image.height * scale)
        return Rect(center(canvas) - Offset(size.width, size.height) / 2f + pan, size)
    }

    fun zoomAt(factor: Float, anchor: Offset, canvas: IntSize, delta: Offset = Offset.Zero): ImageEditorViewport =
        zoomAround(factor, anchor - center(canvas), delta)

    fun zoomAround(factor: Float, anchorFromCenter: Offset, delta: Offset = Offset.Zero): ImageEditorViewport {
        val next = (zoom * factor).coerceIn(0.1f, 8f)
        return copy(zoom = next, pan = pan + (anchorFromCenter - pan) * (1f - next / zoom) + delta)
    }

    private fun center(canvas: IntSize): Offset = Offset(canvas.width / 2f, canvas.height / 2f)
}
