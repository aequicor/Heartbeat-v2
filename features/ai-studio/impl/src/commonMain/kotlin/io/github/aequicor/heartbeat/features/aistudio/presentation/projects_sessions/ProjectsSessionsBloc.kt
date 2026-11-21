@file:Suppress("PackageNaming")

package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.ProjectsSessionsAction.Project
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.ProjectsSessionsAction.ProjectsSessions
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.ProjectsSessionsAction.Session
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace

internal class ProjectsSessionsBloc(private val activity: (String) -> Activity?) {

    var state = ProjectsSessionsState()
        private set

    private var panel: ProjectsSessionsState.Panel
        get() = state.panel
        set(value) {
            state = state.copy(panel = value)
        }

    fun dispatch(action: ProjectsSessionsAction, workspace: AuroraWorkspace): AuroraWorkspace = when (action) {
        is ProjectsSessions -> workspace.also { updatePanel(action, it) }
        is Project.Toggle -> toggle(projectId(action.name), workspace)
        is Project.ShowAllSessions -> showAllSessions(action.name, workspace)
        is Project.CreateSession -> insert(action.session, workspace)
        is Session.Toggle -> toggle(action.id, workspace)
        is Session.Archive -> archive(action.id, workspace)
        Session.Opened -> workspace.also { panel = panel.copy(visible = false, focusId = null) }
    }

    private fun updatePanel(action: ProjectsSessions, workspace: AuroraWorkspace) {
        when (action) {
            is ProjectsSessions.Search -> {
                panel = panel.copy(query = action.query, focusId = null)
                refresh(workspace)
            }
            is ProjectsSessions.ShowSearch -> {
                if (action.visible) {
                    panel = panel.copy(searchVisible = true)
                } else {
                    panel = panel.copy(searchVisible = false, query = "", focusId = null)
                    refresh(workspace)
                }
            }
            is ProjectsSessions.ShowPanel -> panel = panel.copy(visible = action.visible)
            is ProjectsSessions.FocusHandled -> if (panel.focusId == action.id) panel = panel.copy(focusId = null)
        }
    }

    fun refresh(workspace: AuroraWorkspace) {
        val grouped = workspace.activeChats().withIndex().groupBy { it.value.project }
        val (ungrouped, projects) = grouped.entries.partition { it.key == Ungrouped }
        state = state.copy(projects = (projects + ungrouped).map { (name, sessions) ->
            projectState(name, sessions, workspace)
        })
    }

    private fun toggle(id: String, workspace: AuroraWorkspace): AuroraWorkspace {
        val collapsed = workspace.collapsedNavigation
        val next = workspace.copy(collapsedNavigation = if (id in collapsed) collapsed - id else collapsed + id)
        refresh(next)
        panel = panel.copy(focusId = null)
        return next
    }

    private fun showAllSessions(project: String, workspace: AuroraWorkspace): AuroraWorkspace {
        val index = state.rows().indexOfFirst { it.kind == NavigationKind.More && it.project == project }
        if (index < 0) return workspace
        val next = workspace.copy(expandedProjects = workspace.expandedProjects + project)
        refresh(next)
        panel = panel.copy(focusId = state.rows().getOrNull(index)?.id)
        return next
    }

    private fun archive(id: String, workspace: AuroraWorkspace): AuroraWorkspace {
        if (workspace.activeChats().none { it.id == id }) return workspace
        val index = state.rows().indexOfFirst { it.id == id }
        val next = workspace.copy(archivedChats = workspace.archivedChats + id)
        refresh(next)
        val rows = state.rows()
        val focus = if (index < 0) null else rows.drop(index).firstOrNull { it.kind != NavigationKind.More }
            ?: rows.lastOrNull { it.kind != NavigationKind.More }
        panel = panel.copy(focusId = focus?.id)
        return next
    }

    private fun insert(session: Chat, workspace: AuroraWorkspace): AuroraWorkspace {
        val chats = workspace.chats.toMutableList()
        val index = chats.indexOfFirst { it.summary.project == session.project }.coerceAtLeast(0)
        chats.add(index, AuroraChat(session))
        return workspace.copy(
            chats = chats,
            collapsedNavigation = workspace.collapsedNavigation - projectId(session.project),
        )
    }

    private fun projectState(
        project: String,
        sessions: List<IndexedValue<Chat>>,
        workspace: AuroraWorkspace,
    ): ProjectsSessionsState.Project {
        val ungrouped = project == Ungrouped
        val id = projectId(project)
        val branches = sessions.groupBy { it.value.parentId?.takeUnless(workspace.archivedChats::contains) }
        val limited = !ungrouped && panel.query.isEmpty() && project !in workspace.expandedProjects
        return ProjectsSessionsState.Project(
            id = id,
            name = project,
            kind = if (ungrouped) NavigationKind.Section else NavigationKind.Project,
            expanded = expanded(id, workspace),
            sessionLimit = SessionLimit.takeIf { limited },
            sessions = branches[null].orEmpty().map { session ->
                val children = branches[session.value.id].orEmpty().map { sessionState(it, workspace) }
                sessionState(session, workspace, children)
            },
        )
    }

    private fun sessionState(
        session: IndexedValue<Chat>,
        workspace: AuroraWorkspace,
        children: List<ProjectsSessionsState.Session> = listOf(),
    ): ProjectsSessionsState.Session {
        val (position, chat) = session
        val status = when (activity(chat.id)) {
            Activity.Waiting -> NavigationStatus.Attention
            Activity.Running, Activity.Sending, Activity.Stopping -> NavigationStatus.Working
            Activity.Idle, null -> chat.navigationStatus
        }
        return ProjectsSessionsState.Session(
            chat = chat,
            position = position,
            matches = chat.title.contains(panel.query, ignoreCase = true) ||
                chat.project.contains(panel.query, ignoreCase = true),
            selected = chat.id == workspace.selectedId,
            status = status,
            expanded = expanded(chat.id, workspace),
            children = children,
        )
    }

    private fun expanded(id: String, workspace: AuroraWorkspace): Boolean =
        panel.query.isNotEmpty() || id !in workspace.collapsedNavigation

    private fun projectId(project: String): String = "project:$project"

    companion object {
        const val Ungrouped = "Без проекта"
        private const val SessionLimit = 5
    }
}
