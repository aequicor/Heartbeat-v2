package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlin.time.Instant

internal data class StudioSession(
    val id: StudioSessionId,
    val projectId: ProjectId,
    val title: String,
    val status: SessionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val parentSessionId: StudioSessionId? = null,
    val isExpanded: Boolean = true,
)
