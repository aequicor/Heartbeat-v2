package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSession
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.StudioSessionRepository
import kotlin.time.Instant

internal class StudioSessionInteractor(private val repository: StudioSessionRepository) {

    suspend fun start(
        projectId: ProjectId,
        title: String,
        sentAt: Instant,
        parentSessionId: StudioSessionId? = null,
    ): StudioSession = repository.start(projectId, title, sentAt, parentSessionId)

    suspend fun findById(id: StudioSessionId): StudioSession? = repository.findById(id)

    suspend fun listRoots(
        projectId: ProjectId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<StudioSessionId>? = null,
    ): Page<StudioSession, StudioSessionId> = repository.listRoots(projectId, status, limit, after)

    suspend fun listChildren(
        parentSessionId: StudioSessionId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<StudioSessionId>? = null,
    ): Page<StudioSession, StudioSessionId> = repository.listChildren(parentSessionId, status, limit, after)

    suspend fun rename(id: StudioSessionId, title: String) = repository.rename(id, title)

    suspend fun setExpanded(id: StudioSessionId, isExpanded: Boolean) = repository.setExpanded(id, isExpanded)

    suspend fun setStatus(id: StudioSessionId, status: SessionStatus, changedAt: Instant) =
        repository.setStatus(id, status, changedAt)
}
