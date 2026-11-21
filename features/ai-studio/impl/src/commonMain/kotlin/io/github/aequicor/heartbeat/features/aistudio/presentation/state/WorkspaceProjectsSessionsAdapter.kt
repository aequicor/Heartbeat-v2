package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.ProjectsSessionsState
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ListFocusTarget
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ProjectListItem
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.SavedProjectsSessions
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.SessionListItem
import kotlin.uuid.Uuid

/** Bridges the demo workspace's string keys and the public screen contract for this screen's lifetime. */
internal class WorkspaceProjectsSessionsAdapter {

    private val ids = mutableMapOf<String, Uuid>()
    private val keys = mutableMapOf<Uuid, String>()

    fun projectId(key: String): ProjectId = ProjectId(uuid(key))

    fun sessionId(key: String): StudioSessionId = StudioSessionId(uuid(key))

    fun sessionKey(id: StudioSessionId): String = keys.getValue(id.value)

    private fun uuid(key: String): Uuid = ids.getOrPut(key) {
        Uuid.random().also { keys[it] = key }
    }

    fun saved(workspace: AuroraWorkspace): SavedProjectsSessions {
        val (projects, sessions) = workspace.collapsedNavigation.partition { it.startsWith(ProjectPrefix) }
        return SavedProjectsSessions(
            collapsedProjects = projects.map { projectId(it.removePrefix(ProjectPrefix)) }.toSet(),
            collapsedSessions = sessions.map(::sessionId).toSet(),
            expandedProjects = workspace.expandedProjects.map(::projectId).toSet(),
            archivedSessions = workspace.archivedChats.map(::sessionId).toSet(),
        )
    }

    fun snapshot(workspace: AuroraWorkspace, saved: SavedProjectsSessions): AuroraWorkspace = workspace.copy(
        collapsedNavigation = buildSet {
            saved.collapsedProjects.forEach { add(projectKey(it)) }
            saved.collapsedSessions.forEach { add(sessionKey(it)) }
        },
        expandedProjects = saved.expandedProjects.map { keys.getValue(it.value) }.toSet(),
        archivedChats = saved.archivedSessions.map(::sessionKey).toSet(),
    )

    fun projects(workspace: AuroraWorkspace, activity: (String) -> Activity?): List<ProjectListItem> {
        val groups = workspace.chats.groupBy { it.summary.project }
        return groups.keys.sortedBy { it == Ungrouped }.map { title ->
            val members = groups.getValue(title).map { it.summary }
            val idsInProject = members.mapTo(mutableSetOf()) { it.id }
            val byParent = members.groupBy { it.parentId?.takeIf(idsInProject::contains) }

            fun item(chat: Chat): SessionListItem = SessionListItem(
                id = sessionId(chat.id),
                title = chat.title,
                children = byParent[chat.id].orEmpty().map(::item),
                activityBadge = when (activity(chat.id)) {
                    Activity.Waiting -> NavigationStatus.Attention
                    Activity.Running, Activity.Sending, Activity.Stopping -> NavigationStatus.Working
                    Activity.Idle, null -> chat.navigationStatus
                },
            )

            ProjectListItem(
                id = projectId(title),
                title = title,
                sessions = byParent[null].orEmpty().map(::item),
                showAllSessions = title == Ungrouped,
            )
        }
    }

    fun screen(
        screen: AiStudioScreenState,
        list: ProjectsSessionsState,
        workspace: AuroraWorkspace,
    ): AiStudioScreenState {
        val matches = mutableSetOf<String>()
        fun collectMatches(session: SessionListItem) {
            if (session.matchesSearch) matches.add(sessionKey(session.id))
            session.children.forEach(::collectMatches)
        }

        val rows = buildList {
            for (project in list.projects) {
                val kind = if (project.title == Ungrouped) NavigationKind.Section else NavigationKind.Project
                add(
                    NavigationRow(
                        projectKey(project.id),
                        project.title,
                        kind,
                        project.title,
                        expanded = project.expanded
                    ),
                )
                project.sessions.forEach(::collectMatches)
                for (session in project.visibleSessions) addBranch(session, project.title)
                if (project.hasMore) {
                    add(NavigationRow("more:${project.title}", "Ещё диалоги", NavigationKind.More, project.title))
                }
            }
        }
        return screen.copy(
            chats = workspace.chats.map { it.summary }.filter { it.id in matches },
            chatSearch = list.query,
            chatSearchVisible = list.searchVisible,
            navigation = rows,
            navigationFocusId = list.focus?.let(::focusKey),
        )
    }

    fun focusKey(target: ListFocusTarget): String = when (target) {
        is ListFocusTarget.Project -> projectKey(target.id)
        is ListFocusTarget.Session -> sessionKey(target.id)
    }

    private fun projectKey(id: ProjectId): String = ProjectPrefix + keys.getValue(id.value)

    private fun MutableList<NavigationRow>.addBranch(
        session: SessionListItem,
        project: String,
        depth: Int = 0,
        lastChild: Boolean = false,
    ) {
        add(
            NavigationRow(
                id = sessionKey(session.id),
                title = session.title,
                kind = NavigationKind.Chat,
                project = project,
                depth = depth,
                expanded = session.expanded.takeIf { session.children.isNotEmpty() },
                selected = session.selected,
                status = session.activityBadge,
                lastChild = lastChild,
            )
        )
        if (session.expanded) session.children.forEachIndexed { index, child ->
            addBranch(child, project, depth + 1, index == session.children.lastIndex)
        }
    }

    companion object {

        const val Ungrouped = "Без проекта"
        private const val ProjectPrefix = "project:"
    }
}
