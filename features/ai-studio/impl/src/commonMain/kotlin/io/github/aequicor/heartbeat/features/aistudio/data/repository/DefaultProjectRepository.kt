package io.github.aequicor.heartbeat.features.aistudio.data.repository

import io.github.aequicor.heartbeat.features.aistudio.data.dao.ProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.ProjectDto
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectRepository
import okio.Path
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

internal class DefaultProjectRepository(private val dao: ProjectDao) : ProjectRepository {

    override suspend fun create(immutableName: String, workingDirectory: Path): ProjectCreationResult = try {
        accessStudioRepository {
            ProjectCreationResult.Created(dao.create(immutableName, workingDirectory.toString()).toProject())
        }
    } catch (conflict: ProjectNameAlreadyExistsException) {
        ProjectCreationResult.NameAlreadyExists(conflict.immutableName)
    }

    override suspend fun findById(id: ProjectId): Project? =
        accessStudioRepository { dao.findById(id.value.toString())?.toProject() }

    override suspend fun list(
        status: ProjectStatus,
        limit: Int,
        after: PageCursor<ProjectId>?,
    ): Page<Project, ProjectId> = accessStudioRepository {
        val page = dao.list(status.name, limit, after?.toDaoCursor { it.value.toString() })
        val nextCursor = page.nextCursor?.let { PageCursor(it.updatedAt.toInstant(), ProjectId(Uuid.parse(it.id))) }
        Page(page.items.map(ProjectDto::toProject), nextCursor)
    }

    override suspend fun renameTitle(id: ProjectId, title: String) =
        accessStudioRepository { dao.renameTitle(id.value.toString(), title) }

    override suspend fun setExpanded(id: ProjectId, isExpanded: Boolean) =
        accessStudioRepository { dao.setExpanded(id.value.toString(), isExpanded) }

    override suspend fun setStatus(id: ProjectId, status: ProjectStatus) =
        accessStudioRepository { dao.setStatus(id.value.toString(), status.name) }
}

private fun ProjectDto.toProject() = Project(
    id = ProjectId(Uuid.parse(id)),
    immutableName = immutableName,
    title = title,
    workingDirectory = workingDirectory.toPath(),
    status = ProjectStatus.valueOf(status),
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    isExpanded = isExpanded,
)
