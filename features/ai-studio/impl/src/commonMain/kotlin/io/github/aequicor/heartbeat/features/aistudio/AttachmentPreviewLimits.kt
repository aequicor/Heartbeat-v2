package io.github.aequicor.heartbeat.features.aistudio

internal const val MaxAttachmentImagePreviewBytes = 16 * 1024 * 1024

// A lossless copy can be larger than the source JPEG. Bound it by the rendered pixel budget.
private const val MaxEditedImagePreviewBytes = 68 * 1024 * 1024

internal const val EditedImageKindPrefix = "PNG · "

internal fun attachmentImagePreviewLimit(kind: String): Int =
    if (kind.startsWith(EditedImageKindPrefix)) MaxEditedImagePreviewBytes else MaxAttachmentImagePreviewBytes

internal fun ByteArray.editedPngKind(): String {
    if (size < 24) return "${EditedImageKindPrefix}Копия"
    fun readInt(index: Int): Int =
        (index until index + 4).fold(0) { value, next -> (value shl 8) or (this[next].toInt() and 0xff) }
    return "$EditedImageKindPrefix${readInt(16)} × ${readInt(20)}"
}
