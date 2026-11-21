package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions

import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ListFocusTarget
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ProjectListItem
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.SessionListItem

internal data class ProjectsSessionsState(
    val projects: List<ProjectListItem> = listOf(),
    val query: String = "",
    val searchVisible: Boolean = false,
    val focus: ListFocusTarget? = null,
) {

    fun focusableItems(): List<ListFocusTarget> = buildList {
        fun addSession(session: SessionListItem) {
            add(ListFocusTarget.Session(session.id))
            if (session.expanded) session.children.forEach(::addSession)
        }

        for (project in projects) {
            add(ListFocusTarget.Project(project.id))
            project.visibleSessions.forEach(::addSession)
        }
    }
}
