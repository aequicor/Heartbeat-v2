package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class AttachmentFile(
    val id: String,
    val name: String,
    val location: AttachmentLocation,
    val description: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)
