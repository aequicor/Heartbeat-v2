package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ListFocusTarget

internal sealed interface ProjectsSessionsEvent {

    data class SearchChanged(val query: String) : ProjectsSessionsEvent
    data class SearchVisibilityChanged(val visible: Boolean) : ProjectsSessionsEvent
    data class ProjectToggled(val id: ProjectId) : ProjectsSessionsEvent
    data class SessionToggled(val id: StudioSessionId) : ProjectsSessionsEvent
    data class MoreSessionsRequested(val projectId: ProjectId) : ProjectsSessionsEvent
    data class SessionClicked(val id: StudioSessionId) : ProjectsSessionsEvent
    data class ArchiveSessionRequested(val id: StudioSessionId) : ProjectsSessionsEvent
    data class FocusHandled(val target: ListFocusTarget) : ProjectsSessionsEvent
}
