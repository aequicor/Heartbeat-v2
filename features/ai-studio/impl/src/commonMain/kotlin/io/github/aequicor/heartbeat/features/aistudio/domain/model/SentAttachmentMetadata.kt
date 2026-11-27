package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class SentAttachmentMetadata(
    val fileId: String,
    val name: String,
    val description: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
)
