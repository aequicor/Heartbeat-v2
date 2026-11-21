package io.github.aequicor.heartbeat.features.aistudio.data.model

internal data class ProjectDto(
    val id: String,
    val immutableName: String,
    val title: String,
    val workingDirectory: String,
    val status: String,
    val createdAt: DatabaseTimestamp,
    val updatedAt: DatabaseTimestamp,
    val isExpanded: Boolean,
)
