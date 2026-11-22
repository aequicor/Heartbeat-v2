package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import okio.Path

internal interface ProjectDataSource {

    suspend fun create(immutableName: String, workingDirectory: Path): ProjectCreationResult

    suspend fun findById(id: ProjectId): Project?

    suspend fun list(
        status: ProjectStatus,
        limit: Int,
        after: PageCursor<ProjectId>? = null,
    ): Page<Project, ProjectId>

    suspend fun renameTitle(id: ProjectId, title: String)

    suspend fun setExpanded(id: ProjectId, isExpanded: Boolean)

    suspend fun setStatus(id: ProjectId, status: ProjectStatus)

    suspend fun delete(id: ProjectId)
}
