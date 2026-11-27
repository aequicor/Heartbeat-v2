package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.Throws as Throwable

internal data class AttachmentImageEdit(
    val turns: Int = 0,
    val crop: Rect = Rect(0f, 0f, 1f, 1f),
) {

    fun rotatedSize(image: ImageBitmap): IntSize =
        if (turns % 2 == 0) IntSize(image.width, image.height) else IntSize(image.height, image.width)

    fun rotateRight() = AttachmentImageEdit(
        (turns + 1) % 4,
        Rect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right),
    )

    fun draw(canvas: Canvas, image: ImageBitmap, paint: Paint) {
        canvas.save()
        canvas.translate(
            if (turns == 1 || turns == 2) rotatedSize(image).width.toFloat() else 0f,
            if (turns == 2 || turns == 3) rotatedSize(image).height.toFloat() else 0f,
        )
        canvas.rotate(turns * 90f)
        canvas.drawImage(image, Offset.Zero, paint)
        canvas.restore()
    }

    fun render(image: ImageBitmap): ImageBitmap {
        val size = rotatedSize(image)
        val left = (crop.left * size.width).roundToInt().coerceIn(0, size.width - 1)
        val top = (crop.top * size.height).roundToInt().coerceIn(0, size.height - 1)
        val right = (crop.right * size.width).roundToInt().coerceIn(left + 1, size.width)
        val bottom = (crop.bottom * size.height).roundToInt().coerceIn(top + 1, size.height)
        return ImageBitmap(right - left, bottom - top).also { cropped ->
            val canvas = Canvas(cropped)
            canvas.translate(-left.toFloat(), -top.toFloat())
            draw(canvas, image, Paint())
        }
    }
}

@Throwable(IllegalStateException::class)
internal expect fun ImageBitmap.encodeAttachmentPng(): ByteArray
