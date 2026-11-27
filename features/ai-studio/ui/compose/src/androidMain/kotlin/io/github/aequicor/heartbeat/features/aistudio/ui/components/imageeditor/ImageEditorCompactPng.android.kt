package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

internal actual fun deflateImageEditorScanlines(scanlines: ByteArray): ByteArray {
    val compressed = ByteArrayOutputStream()
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    try {
        DeflaterOutputStream(compressed, deflater).use { it.write(scanlines) }
    } finally {
        deflater.end()
    }
    return compressed.toByteArray()
}
