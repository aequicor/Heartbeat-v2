package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.graphics.ImageBitmap

internal data class ImageEditorPreparedCopy(
    val png: ByteArray,
    val thumbnail: String,
    val preview: ImageBitmap,
)
