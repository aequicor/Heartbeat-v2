package io.github.aequicor.heartbeat.features.aistudio.domain.model

import okio.Path
import kotlin.time.Instant

internal data class Project(
    val id: ProjectId,
    val immutableName: String,
    val title: String,
    val workingDirectory: Path,
    val status: ProjectStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isExpanded: Boolean = true,
)
