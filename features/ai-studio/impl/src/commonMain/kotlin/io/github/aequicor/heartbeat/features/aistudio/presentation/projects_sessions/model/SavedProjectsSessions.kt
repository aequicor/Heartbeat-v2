package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId

internal data class SavedProjectsSessions(
    val collapsedProjects: Set<ProjectId> = setOf(),
    val collapsedSessions: Set<StudioSessionId> = setOf(),
    val expandedProjects: Set<ProjectId> = setOf(),
    val archivedSessions: Set<StudioSessionId> = setOf(),
)
