package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.model.StudioSessionDto
import io.github.aequicor.heartbeat.features.aistudio.db.StudioSessionQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Studio_session
import kotlin.uuid.Uuid

internal class SqlDelightStudioSessionDao(
    private val connection: DatabaseConnection,
) : StudioSessionDao {
    private val queries get() = StudioSessionQueries(connection.driver)

    override suspend fun start(
        projectId: String,
        title: String,
        sentAt: DatabaseTimestamp,
        parentSessionId: String?,
    ): StudioSessionDto = accessStudioDatabase("Start studio session") {
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

    override suspend fun findById(id: String): StudioSessionDto? =
        accessStudioDatabase("Find studio session") {
            queries.selectSessionById(id).executeAsOneOrNull()?.toSessionDto()
        }

    override suspend fun listRoots(
        projectId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<StudioSessionDto> {
        require(limit > 0)
        return accessStudioDatabase("List root studio sessions") {
            val rows = queries.listRootSessions(
                projectId,
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_session::toSessionDto)
            studioPage(rows, limit, StudioSessionDto::toCursor)
        }
    }

    override suspend fun listChildren(
        parentSessionId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<StudioSessionDto> {
        require(limit > 0)
        return accessStudioDatabase("List child studio sessions") {
            val rows = queries.listChildSessions(
                parentSessionId,
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_session::toSessionDto)
            studioPage(rows, limit, StudioSessionDto::toCursor)
        }
    }

    override suspend fun rename(id: String, title: String) {
        accessStudioDatabase("Rename studio session") { queries.renameSession(title, id) }
    }

    override suspend fun setStatus(id: String, status: String, changedAt: DatabaseTimestamp) {
        accessStudioDatabase("Set studio session status") {
            queries.setSessionStatus(status, changedAt.epochSeconds, changedAt.nanoseconds, id)
        }
    }

    /** Records a sent message or completed agent run on the current transaction, when present. */
    fun recordActivity(id: String, at: DatabaseTimestamp) {
        accessStudioDatabase("Record studio session activity") {
            queries.recordSessionActivity(at.epochSeconds, at.nanoseconds, id)
        }
    }
}

private fun Studio_session.toSessionDto() = StudioSessionDto(
    id,
    project_id,
    title,
    status,
    DatabaseTimestamp(created_at_seconds, created_at_nanos),
    DatabaseTimestamp(updated_at_seconds, updated_at_nanos),
    parent_session_id,
)

private fun StudioSessionDto.toCursor() = DaoPageCursor(updatedAt, id)
