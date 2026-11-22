package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectTree

internal interface ProjectsSessionsSearchDataSource {
    suspend fun search(query: String): List<ProjectTree>
}
