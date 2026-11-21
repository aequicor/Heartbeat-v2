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

    override suspend fun create(immutableName: String, workingDirectory: String): ProjectDto {
        val id = Uuid.random().toString()
        val project = accessStudioDatabase("Create project") {
            val now = clock.now()
            val projectQueries = queries
            projectQueries.transactionWithResult {
                projectQueries.insertProject(
                    id,
                    immutableName,
                    workingDirectory,
                    now.epochSeconds,
                    now.nanosecondsOfSecond.toLong(),
                )
                projectQueries.selectProjectByImmutableName(immutableName).executeAsOne().toProjectDto()
            }
        }
        if (project.id != id) throw ProjectNameAlreadyExistsException(immutableName)
        return project
    }

    override suspend fun findById(id: String): ProjectDto? =
        accessStudioDatabase("Find project") {
            queries.selectProjectById(id).executeAsOneOrNull()?.toProjectDto()
        }

    override suspend fun list(
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<ProjectDto> {
        require(limit > 0)
        return accessStudioDatabase("List projects") {
            val rows = queries.listProjects(
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_project::toProjectDto)
            studioPage(rows, limit) { DaoPageCursor(it.updatedAt, it.id) }
        }
    }

    override suspend fun renameTitle(id: String, title: String) {
        accessStudioDatabase("Rename project title") {
            val now = clock.now()
            queries.renameProjectTitle(
                title,
                now.epochSeconds,
                now.nanosecondsOfSecond.toLong(),
                id,
            )
        }
    }

    override suspend fun setExpanded(id: String, isExpanded: Boolean) {
        accessStudioDatabase("Set project expansion") {
            queries.setProjectExpanded(if (isExpanded) 1L else 0L, id)
        }
    }

    override suspend fun setStatus(id: String, status: String) {
        accessStudioDatabase("Set project status") {
            val now = clock.now()
            queries.setProjectStatus(status, now.epochSeconds, now.nanosecondsOfSecond.toLong(), id)
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
