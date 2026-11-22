package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.ProjectDto
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import okio.Path
import okio.Path.Companion.toPath

@Inject
internal class DefaultProjectDataSource(private val dao: ProjectDao) : ProjectDataSource {

    override suspend fun create(immutableName: String, workingDirectory: Path): ProjectCreationResult = try {
        accessStudioRepository {
            val project = dao.insert(immutableName, workingDirectory.toString())
            ProjectCreationResult.Created(project.toProjectWithDirectory())
        }
    } catch (conflict: ProjectNameAlreadyExistsException) {
        ProjectCreationResult.NameAlreadyExists(conflict.immutableName)
    }

    override suspend fun findById(id: ProjectId): Project? =
        accessStudioRepository { dao.selectById(id.value.toString())?.toProject() }

    override suspend fun list(
        status: ProjectStatus,
        limit: Int,
        after: PageCursor<ProjectId>?,
    ): Page<Project, ProjectId> = accessStudioRepository {
        val page = dao.select(status.name, limit, after?.toDaoCursor { it.value.toString() })
        val nextCursor = page.nextCursor?.toPageCursor(ProjectId.Companion::parse)
        Page(items = page.items.map(ProjectDto::toProject), nextCursor = nextCursor)
    }

    override suspend fun renameTitle(id: ProjectId, title: String) =
        accessStudioRepository { dao.updateTitle(id.value.toString(), title) }

    override suspend fun setExpanded(id: ProjectId, isExpanded: Boolean) =
        accessStudioRepository { dao.updateExpanded(id.value.toString(), isExpanded) }

    override suspend fun setStatus(id: ProjectId, status: ProjectStatus) =
        accessStudioRepository { dao.updateStatus(id.value.toString(), status.name) }

    override suspend fun delete(id: ProjectId) =
        accessStudioRepository { dao.delete(id.value.toString()) }
}

internal fun ProjectDto.toProject(): Project = if (id == ProjectId.Ungrouped.value.toString()) {
    Project.WithoutDirectory(isExpanded = isExpanded)
} else {
    toProjectWithDirectory()
}

private fun ProjectDto.toProjectWithDirectory(): Project.WithDirectory {
    check(workingDirectory.isNotEmpty()) { "Project $id has no working directory" }
    return Project.WithDirectory(
        id = ProjectId.parse(id),
        immutableName = immutableName,
        title = title,
        workingDirectory = workingDirectory.toPath(),
        status = ProjectStatus.valueOf(status),
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        isExpanded = isExpanded,
    )
}
