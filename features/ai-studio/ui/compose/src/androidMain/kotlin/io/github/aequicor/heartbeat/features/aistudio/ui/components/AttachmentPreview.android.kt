package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment

internal actual fun decodeAttachmentPreview(bytes: ByteArray, image: ImageAttachment): ImageBitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outMimeType == image.mimeType && image.acceptsDimensions(options.outWidth, options.outHeight))
    options.inJustDecodeBounds = false
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).asImageBitmap()
}
