package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlin.time.Instant

internal data class PageCursor<Id>(
    val updatedAt: Instant,
    val id: Id,
)
