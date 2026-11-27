package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.Throws as Throwable

@Throwable(CancellationException::class, IllegalArgumentException::class)
internal suspend fun ImageBitmap.encodeCompactAttachmentPng(quality: ImageEditorPngQuality): ByteArray {
    val levels = quality.colorLevels
    require(levels > 1)
    val row = IntArray(width)
    val context = currentCoroutineContext()
    val indexedStride = width + 1
    val indexedRows = ByteArray(indexedStride * height)
    var opaque = true
    for (y in 0 until height) {
        context.ensureActive()
        readPixels(row, startY = y, height = 1)
        if (row.any { it ushr 24 != 255 }) {
            opaque = false
            break
        }
        for (x in row.indices) {
            val argb = row[x]
            val red = quantize(argb ushr 16, levels)
            val green = quantize(argb ushr 8, levels)
            val blue = quantize(argb, levels)
            indexedRows[y * indexedStride + x + 1] = ((red * levels + green) * levels + blue).toByte()
        }
    }
    val scanlines = if (opaque) indexedRows else rgbaRows(row, levels)
    context.ensureActive()
    val colorType: Byte = if (opaque) 3 else 6
    val header = width.pngBytes() + height.pngBytes() + byteArrayOf(8, colorType, 0, 0, 0)
    val palette = if (opaque) pngChunk("PLTE", indexedColors(levels)) else byteArrayOf()
    return pngSignature + pngChunk("IHDR", header) + palette +
        pngChunk("IDAT", deflateImageEditorScanlines(scanlines)) + pngChunk("IEND", byteArrayOf())
}

@Throwable(CancellationException::class)
private suspend fun ImageBitmap.rgbaRows(row: IntArray, levels: Int): ByteArray {
    val stride = 4
    val scanlineSize = width * stride + 1
    val scanlines = ByteArray(scanlineSize * height)
    val previous = IntArray(stride)
    val context = currentCoroutineContext()
    for (y in 0 until height) {
        context.ensureActive()
        readPixels(row, startY = y, height = 1)
        val start = y * scanlineSize
        scanlines[start] = 1 // PNG Sub filter.
        previous.fill(0)
        for (x in row.indices) {
            val argb = row[x]
            val offset = start + x * stride + 1
            scanlines.writeFiltered(offset, previous, 0, quantize(argb ushr 16, levels) * 255 / (levels - 1))
            scanlines.writeFiltered(offset, previous, 1, quantize(argb ushr 8, levels) * 255 / (levels - 1))
            scanlines.writeFiltered(offset, previous, 2, quantize(argb, levels) * 255 / (levels - 1))
            scanlines.writeFiltered(offset, previous, 3, argb ushr 24)
        }
    }
    return scanlines
}

private fun quantize(channel: Int, levels: Int): Int = ((channel and 255) * (levels - 1) + 127) / 255

private fun ByteArray.writeFiltered(offset: Int, previous: IntArray, channel: Int, value: Int) {
    this[offset + channel] = (value - previous[channel]).toByte()
    previous[channel] = value
}

private fun indexedColors(levels: Int): ByteArray = ByteArray(levels * levels * levels * 3) { index ->
    val color = index / 3
    val level = when (index % 3) {
        0 -> color / (levels * levels)
        1 -> color / levels % levels
        else -> color % levels
    }
    (level * 255 / (levels - 1)).toByte()
}

private fun pngChunk(type: String, data: ByteArray): ByteArray {
    val body = type.encodeToByteArray() + data
    val crc = body.fold(-1) { value, byte ->
        (value ushr 8) xor crcTable[(value xor byte.toInt()) and 255]
    }.inv()
    return data.size.pngBytes() + body + crc.pngBytes()
}

private fun Int.pngBytes(): ByteArray =
    byteArrayOf((this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte())

private val crcTable = IntArray(256) { index ->
    var value = index
    repeat(8) {
        value = if (value and 1 != 0) (value ushr 1) xor 0xedb88320.toInt() else value ushr 1
    }
    value
}

private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

internal expect fun deflateImageEditorScanlines(scanlines: ByteArray): ByteArray
