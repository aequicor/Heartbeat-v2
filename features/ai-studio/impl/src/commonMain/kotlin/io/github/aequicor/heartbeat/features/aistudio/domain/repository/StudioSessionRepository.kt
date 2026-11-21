package io.github.aequicor.heartbeat.features.aistudio.domain.repository

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSession
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import kotlin.time.Instant

internal interface StudioSessionRepository {

    suspend fun start(
        projectId: ProjectId,
        title: String,
        sentAt: Instant,
        parentSessionId: StudioSessionId? = null,
    ): StudioSession

    suspend fun findById(id: StudioSessionId): StudioSession?

    suspend fun listRoots(
        projectId: ProjectId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<StudioSessionId>? = null,
    ): Page<StudioSession, StudioSessionId>

    suspend fun listChildren(
        parentSessionId: StudioSessionId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<StudioSessionId>? = null,
    ): Page<StudioSession, StudioSessionId>

    suspend fun rename(id: StudioSessionId, title: String)

    suspend fun setStatus(id: StudioSessionId, status: SessionStatus, changedAt: Instant)
}
