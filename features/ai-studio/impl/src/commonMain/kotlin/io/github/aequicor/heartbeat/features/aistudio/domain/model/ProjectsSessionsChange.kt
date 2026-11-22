package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal sealed interface ProjectsSessionsChange {
    data class ProjectAdded(val id: ProjectId) : ProjectsSessionsChange
    data class SessionAdded(val session: Session, val project: Project) : ProjectsSessionsChange
}
