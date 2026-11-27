package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class AttachmentCopyTarget(
    val projectId: String,
    val scopeId: String,
    val isDraft: Boolean,
    val generatedImage: Boolean,
)
