package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectRepository
import okio.Path

internal class ProjectInteractor(private val repository: ProjectRepository) {

    suspend fun create(immutableName: String, workingDirectory: Path): ProjectCreationResult =
        repository.create(immutableName, workingDirectory)

    suspend fun findById(id: ProjectId): Project? = repository.findById(id)

    suspend fun list(
        status: ProjectStatus,
        limit: Int,
        after: PageCursor<ProjectId>? = null,
    ): Page<Project, ProjectId> = repository.list(status, limit, after)

    suspend fun renameTitle(id: ProjectId, title: String) = repository.renameTitle(id, title)

    suspend fun setStatus(id: ProjectId, status: ProjectStatus) = repository.setStatus(id, status)
}
