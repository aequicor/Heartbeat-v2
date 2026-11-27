package io.github.aequicor.heartbeat.features.aistudio.data.datasource

internal data class PromptReferenceEntry(
    val name: String,
    val uri: String,
    val directory: Boolean,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)
