package io.github.aequicor.heartbeat.features.aistudio.data.dao

import app.cash.sqldelight.SuspendingTransacterImpl
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.model.SessionDto
import io.github.aequicor.heartbeat.features.aistudio.db.StudioSessionQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Studio_session
import kotlin.uuid.Uuid

internal class SqlDelightSessionDao(
    private val connection: DatabaseConnection,
) : SessionDao {
    private val queries get() = StudioSessionQueries(connection.driver)

    override suspend fun <T> transaction(block: suspend () -> T): T = accessStudioDatabase("Save studio session") {
        val transacter = object : SuspendingTransacterImpl(connection.driver) {}
        transacter.transactionWithResult { block() }
    }

    override suspend fun insert(
        projectId: String,
        title: String,
        sentAt: DatabaseTimestamp,
        parentSessionId: String?,
    ): SessionDto = accessStudioDatabase("Insert studio session") {
        val id = Uuid.random().toString()
        val sessionQueries = queries
        sessionQueries.transactionWithResult {
            val owningProjectId = if (parentSessionId == null) {
                sessionQueries.selectSessionProject(projectId).executeAsOneOrNull()
            } else {
                sessionQueries.selectParentProject(parentSessionId).executeAsOneOrNull()
            }
            require(owningProjectId == projectId) {
                "The session requires an existing project and a parent in that project"
            }
            sessionQueries.insertSession(
                id,
                projectId,
                title,
                sentAt.epochSeconds,
                sentAt.nanoseconds,
                parentSessionId,
            )
            sessionQueries.selectSessionById(id).executeAsOne().toSessionDto()
        }
    }

    override suspend fun selectById(id: String): SessionDto? =
        accessStudioDatabase("Select studio session by ID") {
            queries.selectSessionById(id).executeAsOneOrNull()?.toSessionDto()
        }

    override suspend fun selectRoots(
        projectId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<SessionDto> {
        require(limit > 0)
        return accessStudioDatabase("Select root studio sessions") {
            val rows = queries.selectRootSessions(
                projectId,
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_session::toSessionDto)
            studioPage(rows, limit, SessionDto::toCursor)
        }
    }

    override suspend fun selectChildren(
        parentSessionId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<SessionDto> {
        require(limit > 0)
        return accessStudioDatabase("Select child studio sessions") {
            val rows = queries.selectChildSessions(
                parentSessionId,
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_session::toSessionDto)
            studioPage(rows, limit, SessionDto::toCursor)
        }
    }

    override suspend fun updateTitle(id: String, title: String) {
        accessStudioDatabase("Update studio session title") { queries.updateSessionTitle(title, id) }
    }

    override suspend fun updateExpanded(id: String, isExpanded: Boolean) {
        accessStudioDatabase("Update session expansion") {
            queries.updateSessionExpanded(if (isExpanded) 1L else 0L, id)
        }
    }

    override suspend fun updateStatus(id: String, status: String, changedAt: DatabaseTimestamp) {
        accessStudioDatabase("Update studio session status") {
            queries.updateSessionStatus(status, changedAt.epochSeconds, changedAt.nanoseconds, id)
        }
    }

    override suspend fun delete(id: String) {
        accessStudioDatabase("Delete studio session tree") { queries.deleteSessionTree(id) }
    }

    /** Updates the session's activity timestamp if newer, joining the current transaction when present. */
    fun updateActivity(id: String, at: DatabaseTimestamp) {
        accessStudioDatabase("Update studio session activity") {
            queries.updateSessionActivity(at.epochSeconds, at.nanoseconds, id)
        }
    }
}

private fun Studio_session.toSessionDto() = SessionDto(
    id,
    project_id,
    title,
    status,
    DatabaseTimestamp(created_at_seconds, created_at_nanos),
    DatabaseTimestamp(updated_at_seconds, updated_at_nanos),
    parent_session_id,
    isExpanded = is_expanded == 1L,
    workState = work_state,
)

private fun SessionDto.toCursor() = DaoPageCursor(updatedAt, id)
