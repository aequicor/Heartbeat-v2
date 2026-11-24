package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlin.io.encoding.Base64

internal enum class ChatImage(val mimeType: String, val extension: String) {
    Png("image/png", "png"),
    Jpeg("image/jpeg", "jpg"),
    Gif("image/gif", "gif"),
    WebP("image/webp", "webp");

    private fun recognizes(bytes: ByteArray): Boolean = when (this) {
        Png -> ChatPng.hasSignature(bytes)
        Jpeg -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        Gif -> bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a")
        WebP -> bytes.startsWithAscii("RIFF") && bytes.startsWithAscii("WEBP", offset = 8)
    }

    fun decode(encoded: String): ByteArray {
        if (this == Png) return ChatPng.decode(encoded)
        require(encoded.length <= (MaxBytes + 2) / 3 * 4)
        return Base64.decode(encoded).also { require(it.size <= MaxBytes && recognizes(it)) }
    }

    companion object {
        const val MaxBytes = 1_048_576
        const val ThumbnailSize = 96
        private const val MaxPixels = 16_777_216L
        private const val MaxSide = 8192

        fun identify(bytes: ByteArray): ChatImage? = entries.firstOrNull { it.recognizes(bytes) }

        fun forMimeType(mimeType: String?): ChatImage? = entries.firstOrNull { it.mimeType == mimeType }

        fun acceptsDimensions(width: Int, height: Int): Boolean =
            width in 1..MaxSide && height in 1..MaxSide && width.toLong() * height <= MaxPixels
    }
}

private fun ByteArray.startsWithAscii(value: String, offset: Int = 0): Boolean =
    size >= offset + value.length && value.indices.all { this[offset + it] == value[it].code.toByte() }
