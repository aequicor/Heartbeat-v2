package io.github.aequicor.heartbeat.features.aistudio.data.model

internal data class StudioSessionDto(
    val id: String,
    val projectId: String,
    val title: String,
    val status: String,
    val createdAt: DatabaseTimestamp,
    val updatedAt: DatabaseTimestamp,
    val parentSessionId: String? = null,
    val isExpanded: Boolean,
)
