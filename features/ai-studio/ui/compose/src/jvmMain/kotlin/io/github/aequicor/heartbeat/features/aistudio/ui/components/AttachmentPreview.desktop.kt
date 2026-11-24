package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

internal actual fun decodeAttachmentPreview(bytes: ByteArray, image: ImageAttachment): ImageBitmap {
    Data.makeFromBytes(bytes).use { data ->
        Codec.makeFromData(data).use { codec ->
            require(image.acceptsDimensions(codec.width, codec.height))
        }
    }
    return bytes.decodeToImageBitmap()
}
