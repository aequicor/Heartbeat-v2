package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentImportSource
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable

@Throwable(IllegalStateException::class)
internal actual fun ImageBitmap.encodeAttachmentPng(): ByteArray = ByteArrayOutputStream().use {
    check(asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
    it.toByteArray()
}

@Composable
internal actual fun attachmentImageEditSource(): (ByteArray) -> AttachmentImportSource {
    val cache = LocalContext.current.cacheDir
    return { bytes ->
        AttachmentImportSource { consume ->
            withContext(Dispatchers.IO) {
                val directory = File(cache, "attachment-edits")
                check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)
                val file = File.createTempFile("edited-", ".png", directory)
                try {
                    file.writeBytes(bytes)
                    consume(listOf(Uri.fromFile(file).toString()))
                } finally {
                    file.delete()
                }
            }
        }
    }
}
