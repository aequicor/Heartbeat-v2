package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import okio.BufferedSource

internal fun BufferedSource.readAttachmentBytes(maxBytes: Int): ByteArray {
    val stopAfter = maxBytes.toLong() + 1
    request(stopAfter)
    return readByteArray(minOf(stopAfter, buffer.size))
}
