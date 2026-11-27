package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas

/** An orthogonal transform between pixel-edge coordinate spaces. */
internal data class ImageEditTransform(val turns: Int = 0, val origin: Offset = Offset.Zero) {

    fun after(before: ImageEditTransform): ImageEditTransform =
        ImageEditTransform((turns + before.turns) % 4, rotate(before.origin) + origin)

    fun inverse(): ImageEditTransform {
        val opposite = ImageEditTransform((4 - turns) % 4)
        return opposite.copy(origin = opposite.rotate(-origin))
    }

    fun apply(canvas: Canvas) {
        canvas.translate(origin.x, origin.y)
        canvas.rotate(turns * 90f)
    }

    private fun rotate(point: Offset): Offset = when (turns) {
        0 -> point
        1 -> Offset(-point.y, point.x)
        2 -> -point
        else -> Offset(point.y, -point.x)
    }
}
