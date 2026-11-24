package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlin.io.encoding.Base64

internal object ChatPng {
    private const val HeaderSize = 33
    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    fun hasSignature(bytes: ByteArray): Boolean =
        bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }

    fun validate(bytes: ByteArray, thumbnail: Boolean = false) {
        require(bytes.size in HeaderSize..ChatImage.MaxBytes && hasSignature(bytes))
        require(bytes.readNumber(8) == 13 && bytes.readNumber(12) == 0x49484452)
        val width = bytes.readNumber(16)
        val height = bytes.readNumber(20)
        require(ChatImage.acceptsDimensions(width, height))
        if (thumbnail) require(maxOf(width, height) <= ChatImage.ThumbnailSize * 2)
    }

    fun decode(encoded: String, thumbnail: Boolean = false): ByteArray {
        val side = ChatImage.ThumbnailSize
        val limit = if (thumbnail) side * side * 16 else ChatImage.MaxBytes
        require(encoded.length <= (limit + 2) / 3 * 4)
        return Base64.decode(encoded).also {
            require(it.size <= limit)
            validate(it, thumbnail)
        }
    }

    fun decodeOrNull(encoded: String, thumbnail: Boolean = false): ByteArray? = try {
        decode(encoded, thumbnail)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun ByteArray.readNumber(offset: Int): Int =
        (offset until offset + Int.SIZE_BYTES).fold(0) { value, index -> (value shl 8) or (this[index].toInt() and 0xff) }
}
