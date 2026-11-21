package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ListFocusTarget
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ProjectListItem
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.SavedProjectsSessions
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.SessionListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The screen serializes inputs and confirms which session it opened. */
internal class ProjectsSessionsBloc(
    initialSaved: SavedProjectsSessions,
    private val onOpenSession: (StudioSessionId) -> Unit,
) {

    private val mutableState = MutableStateFlow(ProjectsSessionsState())
    val state = mutableState.asStateFlow()

    var saved = initialSaved
        private set

    private var projects: List<ProjectListItem> = listOf()
    private var selectedId: StudioSessionId? = null

    fun updateProjects(
        projects: List<ProjectListItem>,
        selectedId: StudioSessionId,
        clearFocus: Boolean = false,
        revealProject: ProjectId? = null,
    ) {
        this.projects = projects
        this.selectedId = selectedId
        if (revealProject != null) saved = saved.copy(collapsedProjects = saved.collapsedProjects - revealProject)
        val current = state.value
        render(if (clearFocus) current.copy(focus = null) else current)
    }

    fun dispatch(event: ProjectsSessionsEvent) {
        val current = state.value
        when (event) {
            is ProjectsSessionsEvent.SearchChanged -> render(current.copy(query = event.query, focus = null))
            is ProjectsSessionsEvent.SearchVisibilityChanged -> {
                if (event.visible) mutableState.value = current.copy(searchVisible = true)
                else render(current.copy(query = "", searchVisible = false, focus = null))
            }

            is ProjectsSessionsEvent.ProjectToggled -> {
                saved = saved.copy(collapsedProjects = saved.collapsedProjects.toggle(event.id))
                render(current.copy(focus = null))
            }

            is ProjectsSessionsEvent.SessionToggled -> {
                saved = saved.copy(collapsedSessions = saved.collapsedSessions.toggle(event.id))
                render(current.copy(focus = null))
            }

            is ProjectsSessionsEvent.MoreSessionsRequested -> showMore(event.projectId)
            is ProjectsSessionsEvent.SessionClicked -> onOpenSession(event.id)
            is ProjectsSessionsEvent.ArchiveSessionRequested -> archive(event.id)
            is ProjectsSessionsEvent.FocusHandled -> {
                if (current.focus == event.target) mutableState.value = current.copy(focus = null)
            }
        }
    }

    private fun showMore(id: ProjectId) {
        val project = state.value.projects.firstOrNull { it.id == id && it.hasMore } ?: return
        val firstAdded = project.sessions[project.visibleSessions.size]
        saved = saved.copy(expandedProjects = saved.expandedProjects + id)
        render(state.value.copy(focus = ListFocusTarget.Session(firstAdded.id)))
    }

    private fun archive(id: StudioSessionId) {
        if (id in saved.archivedSessions || projects.none { it.sessions.containsSession(id) }) return
        val current = state.value
        val index = current.focusableItems().indexOf(ListFocusTarget.Session(id))
        saved = saved.copy(archivedSessions = saved.archivedSessions + id)
        val next = projectState(current)
        val targets = next.focusableItems()
        val focus = if (index < 0) null else targets.getOrNull(index) ?: targets.lastOrNull()
        mutableState.value = next.copy(focus = focus)
    }

    private fun render(current: ProjectsSessionsState) {
        mutableState.value = projectState(current)
    }

    private fun projectState(current: ProjectsSessionsState): ProjectsSessionsState {
        val query = current.query
        val searching = query.isNotEmpty()
        val visible = projects.mapNotNull { project ->
            val projectMatches = project.title.contains(query, ignoreCase = true)
            val sessions = matchingSessions(project.sessions, query, projectMatches)
            if (sessions.isEmpty() && !projectMatches) return@mapNotNull null
            project.copy(
                sessions = sessions,
                expanded = searching || project.id !in saved.collapsedProjects,
                showAllSessions = searching || project.showAllSessions || project.id in saved.expandedProjects,
            )
        }
        return current.copy(projects = visible)
    }

    private fun matchingSessions(
        sessions: List<SessionListItem>,
        query: String,
        projectMatches: Boolean,
    ): List<SessionListItem> = buildList {
        for (session in sessions) {
            val children = matchingSessions(session.children, query, projectMatches)
            if (session.id in saved.archivedSessions) {
                addAll(children)
                continue
            }
            val matches = projectMatches || session.title.contains(query, ignoreCase = true)
            if (matches || children.isNotEmpty()) {
                add(
                    session.copy(
                        children = children,
                        selected = session.id == selectedId,
                        matchesSearch = matches,
                        expanded = query.isNotEmpty() || session.id !in saved.collapsedSessions,
                    )
                )
            }
        }
    }

    private fun List<SessionListItem>.containsSession(id: StudioSessionId): Boolean =
        any { it.id == id || it.children.containsSession(id) }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}
