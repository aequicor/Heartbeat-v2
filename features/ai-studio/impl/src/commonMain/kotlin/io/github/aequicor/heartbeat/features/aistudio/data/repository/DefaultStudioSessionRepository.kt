package io.github.aequicor.heartbeat.features.aistudio.data.repository

import io.github.aequicor.heartbeat.features.aistudio.data.dao.StudioSessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.StudioSessionDto
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSession
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.StudioSessionRepository
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DefaultStudioSessionRepository(private val dao: StudioSessionDao) : StudioSessionRepository {
    override suspend fun start(
        projectId: ProjectId,
        title: String,
        sentAt: Instant,
        parentSessionId: StudioSessionId?,
    ): StudioSession = accessStudioRepository {
        dao.start(
            projectId = projectId.value.toString(),
            title = title,
            sentAt = sentAt.toDatabaseTimestamp(),
            parentSessionId = parentSessionId?.value?.toString(),
        ).toSession()
    }

    override suspend fun findById(id: StudioSessionId): StudioSession? =
        accessStudioRepository { dao.findById(id.value.toString())?.toSession() }

    override suspend fun listRoots(
        projectId: ProjectId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<StudioSessionId>?,
    ): Page<StudioSession, StudioSessionId> = accessStudioRepository {
        dao.listRoots(
            projectId = projectId.value.toString(),
            status = status.name,
            limit = limit,
            after = after?.toDaoCursor { it.value.toString() },
        ).toSessionPage()
    }

    override suspend fun listChildren(
        parentSessionId: StudioSessionId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<StudioSessionId>?,
    ): Page<StudioSession, StudioSessionId> = accessStudioRepository {
        dao.listChildren(
            parentSessionId = parentSessionId.value.toString(),
            status = status.name,
            limit = limit,
            after = after?.toDaoCursor { it.value.toString() },
        ).toSessionPage()
    }

    override suspend fun rename(id: StudioSessionId, title: String) =
        accessStudioRepository { dao.rename(id.value.toString(), title) }

    override suspend fun setExpanded(id: StudioSessionId, isExpanded: Boolean) =
        accessStudioRepository { dao.setExpanded(id.value.toString(), isExpanded) }

    override suspend fun setStatus(id: StudioSessionId, status: SessionStatus, changedAt: Instant) =
        accessStudioRepository { dao.setStatus(id.value.toString(), status.name, changedAt.toDatabaseTimestamp()) }
}

private fun StudioSessionDto.toSession() = StudioSession(
    StudioSessionId(Uuid.parse(id)),
    ProjectId(Uuid.parse(projectId)),
    title,
    SessionStatus.valueOf(status),
    createdAt.toInstant(),
    updatedAt.toInstant(),
    parentSessionId?.let { StudioSessionId(Uuid.parse(it)) },
    isExpanded = isExpanded,
)

private fun DaoPage<StudioSessionDto>.toSessionPage(): Page<StudioSession, StudioSessionId> {
    val domainCursor = nextCursor?.let { PageCursor(it.updatedAt.toInstant(), StudioSessionId(Uuid.parse(it.id))) }
    return Page(items.map(StudioSessionDto::toSession), domainCursor)
}
