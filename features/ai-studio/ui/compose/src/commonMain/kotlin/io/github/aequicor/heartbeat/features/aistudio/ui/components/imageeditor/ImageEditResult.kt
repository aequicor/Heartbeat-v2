package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import kotlin.io.encoding.Base64

internal data class ImageEditResult(
    val png: ByteArray,
    val thumbnail: String,
    val name: String,
    val size: IntSize,
    val description: ImageEditor,
) {

    fun preview(file: File): File = file.copy(
        name = name,
        kind = "PNG · ${size.width} × ${size.height}",
        content = Base64.encode(png),
        image = ImageAttachment("image/png", thumbnail),
        mimeType = "image/png",
        base64Encoded = true,
        sourceSizeBytes = png.size.toLong(),
        deferredReading = file.deferredReading && !file.exceedsInlineImageLimit,
        description = description.description(),
    )
}

internal fun ImageEditor.resizeMarks(source: IntSize, target: IntSize): ImageEditor {
    if (source == target) return this
    return copy(marks = marks.map { mark ->
        mark.copy(
            x = resizePixel(mark.x, source.width, target.width),
            y = resizePixel(mark.y, source.height, target.height),
        )
    })
}

private fun resizePixel(position: Int, source: Int, target: Int): Int =
    (((position + 0.5) * target) / source).toInt().coerceIn(0, target - 1)
