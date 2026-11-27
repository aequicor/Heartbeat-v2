package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentImportSource
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.Throws as Throwable

@Throwable(IllegalStateException::class)
internal actual fun ImageBitmap.encodeAttachmentPng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).use { image ->
        checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
    }

@Composable
internal actual fun attachmentImageEditSource(): (ByteArray) -> AttachmentImportSource = { bytes ->
    AttachmentImportSource { consume ->
        withContext(Dispatchers.IO) {
            val file = Files.createTempFile("heartbeat-edited-image-", ".png")
            try {
                Files.write(file, bytes)
                consume(listOf(file.toString()))
            } finally {
                Files.deleteIfExists(file)
            }
        }
    }
}
