package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.Throws as Throwable

@Throwable(IllegalStateException::class)
internal expect fun ImageBitmap.encodeAttachmentPng(): ByteArray
