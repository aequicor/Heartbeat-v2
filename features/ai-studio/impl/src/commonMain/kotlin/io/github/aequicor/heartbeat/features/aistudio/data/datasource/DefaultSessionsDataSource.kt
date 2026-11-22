package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConfigRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.SessionDto
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import kotlin.time.Instant

@Inject
internal class DefaultSessionsDataSource(private val dao: SessionDao) : SessionsDataSource {

    override suspend fun <T> transaction(block: suspend () -> T): T =
        accessStudioRepository { dao.transaction(block) }

    override suspend fun lastConfiguration(): ChatConfiguration? = accessStudioRepository {
        dao.lastConfiguration()?.let { ChatConfiguration(it.engineId, it.model, it.reasoning) }
    }

    override suspend fun create(
        projectId: ProjectId,
        title: String,
        createdAt: Instant,
        parentSessionId: SessionId?,
        conversation: ChatConfiguration.Conversation?,
    ): Session = accessStudioRepository {
        dao.insert(
            projectId = projectId.value.toString(),
            title = title,
            sentAt = createdAt.toDatabaseTimestamp(),
            parentSessionId = parentSessionId?.value?.toString(),
            conversation = conversation?.let {
                val config = it.configuration
                ChatConfigRecord.Conversation(ChatConfigRecord(config.engineId, config.model, config.reasoning), it.engineSessionId)
            },
        ).toSession()
    }

    override suspend fun findById(id: SessionId): Session? =
        accessStudioRepository { dao.selectById(id.value.toString())?.toSession() }

    override suspend fun listRoots(
        projectId: ProjectId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<SessionId>?,
    ): Page<Session, SessionId> = accessStudioRepository {
        dao.selectRoots(
            projectId = projectId.value.toString(),
            status = status.name,
            limit = limit,
            after = after?.toDaoCursor { it.value.toString() },
        ).toSessionPage()
    }

    override suspend fun listChildren(
        parentSessionId: SessionId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<SessionId>?,
    ): Page<Session, SessionId> = accessStudioRepository {
        dao.selectChildren(
            parentSessionId = parentSessionId.value.toString(),
            status = status.name,
            limit = limit,
            after = after?.toDaoCursor { it.value.toString() },
        ).toSessionPage()
    }

    override suspend fun rename(id: SessionId, title: String) =
        accessStudioRepository { dao.updateTitle(id.value.toString(), title) }

    override suspend fun setExpanded(id: SessionId, isExpanded: Boolean) =
        accessStudioRepository { dao.updateExpanded(id.value.toString(), isExpanded) }

    override suspend fun setStatus(id: SessionId, status: SessionStatus, changedAt: Instant) =
        accessStudioRepository { dao.updateStatus(id.value.toString(), status.name, changedAt.toDatabaseTimestamp()) }

    override suspend fun delete(id: SessionId) =
        accessStudioRepository { dao.delete(id.value.toString()) }
}

internal fun SessionDto.toSession(): Session = Session(
    id = SessionId.parse(id),
    projectId = ProjectId.parse(projectId),
    title = title,
    status = SessionStatus.valueOf(status),
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    parentSessionId = parentSessionId?.let(SessionId.Companion::parse),
    isExpanded = isExpanded,
    workState = SessionWorkState.valueOf(workState),
)

private fun DaoPage<SessionDto>.toSessionPage(): Page<Session, SessionId> {
    val sessionCursor = nextCursor?.toPageCursor(SessionId.Companion::parse)
    return Page(items = items.map(SessionDto::toSession), nextCursor = sessionCursor)
}
