package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.model.ProjectDto
import io.github.aequicor.heartbeat.features.aistudio.db.StudioProjectQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Studio_project
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SqlDelightProjectDao(
    private val connection: DatabaseConnection,
    private val clock: Clock = Clock.System,
) : ProjectDao {

    private val queries get() = StudioProjectQueries(connection.driver)

    override suspend fun insert(
        immutableName: String,
        workingDirectory: String,
    ): ProjectDao.Registration {
        val id = Uuid.random().toString()
        val project = accessStudioDatabase("Insert project") {
            val now = clock.now()
            val projectQueries = queries
            projectQueries.transactionWithResult {
                val existing = projectQueries.selectActiveProjectByDirectory(workingDirectory).executeAsOneOrNull()
                if (existing != null) {
                    return@transactionWithResult ProjectDao.Registration(existing.toProjectDto(), created = false)
                }
                projectQueries.insertProject(
                    id,
                    immutableName,
                    workingDirectory,
                    now.epochSeconds,
                    now.nanosecondsOfSecond.toLong(),
                )
                val saved = projectQueries.selectProjectByImmutableName(immutableName).executeAsOne()
                ProjectDao.Registration(saved.toProjectDto(), created = true)
            }
        }
        if (project.created && project.project.id != id) throw ProjectNameAlreadyExistsException(immutableName)
        return project
    }

    override suspend fun selectById(id: String): ProjectDto? =
        accessStudioDatabase("Select project by ID") {
            queries.selectProjectById(id).executeAsOneOrNull()?.toProjectDto()
        }

    override suspend fun select(
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<ProjectDto> {
        require(limit > 0)
        return accessStudioDatabase("Select projects") {
            val rows = queries.selectProjects(
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_project::toProjectDto)
            studioPage(rows, limit) { DaoPageCursor(it.updatedAt, it.id) }
        }
    }

    override suspend fun updateTitle(id: String, title: String) {
        accessStudioDatabase("Update project title") {
            val now = clock.now()
            queries.updateProjectTitle(
                title,
                now.epochSeconds,
                now.nanosecondsOfSecond.toLong(),
                id,
            )
        }
    }

    override suspend fun updateExpanded(id: String, isExpanded: Boolean) {
        accessStudioDatabase("Update project expansion") {
            queries.updateProjectExpanded(if (isExpanded) 1L else 0L, id)
        }
    }

    override suspend fun updateStatus(id: String, status: String) {
        accessStudioDatabase("Update project status") {
            val now = clock.now()
            queries.updateProjectStatus(status, now.epochSeconds, now.nanosecondsOfSecond.toLong(), id)
        }
    }

    override suspend fun delete(id: String) {
        accessStudioDatabase("Delete project") {
            val projectQueries = queries
            projectQueries.transaction {
                projectQueries.deleteProjectSessions(id)
                projectQueries.deleteProject(id)
            }
        }
    }
}

private fun Studio_project.toProjectDto() = ProjectDto(
    id = id,
    immutableName = name,
    title = alias,
    workingDirectory = working_directory,
    status = status,
    createdAt = DatabaseTimestamp(created_at_seconds, created_at_nanos),
    updatedAt = DatabaseTimestamp(updated_at_seconds, updated_at_nanos),
    isExpanded = is_expanded == 1L,
)
