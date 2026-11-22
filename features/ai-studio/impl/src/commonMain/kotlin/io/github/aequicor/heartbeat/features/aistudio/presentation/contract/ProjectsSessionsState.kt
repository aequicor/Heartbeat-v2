package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.DeletionError
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationLoadError
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.RenameError
import io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions.ProjectsSessionsPages

@ConsistentCopyVisibility
public data class ProjectsSessionsState internal constructor(
    public val query: String = "",
    public val searchVisible: Boolean = false,
    public val visible: Boolean = false,
    public val selectedSessionId: String? = null,
    internal val deletionError: DeletionError? = null,
    internal val renameError: RenameError? = null,
    internal val storageError: Boolean = false,
    internal val projects: List<Project> = listOf(),
    internal val nextProjectCursor: PageCursor<ProjectId>? = null,
    internal val pages: ProjectsSessionsPages = ProjectsSessionsPages(),
    internal val retryingPage: String? = null,
) {

    internal sealed interface Project {
        val id: String
        val name: String
        val expanded: Boolean
        val sessions: List<SessionItem>
        val nextSessionCursor: PageCursor<SessionId>?

        data class Named(
            override val id: String,
            override val name: String,
            override val expanded: Boolean,
            override val sessions: List<SessionItem>,
            override val nextSessionCursor: PageCursor<SessionId>? = null,
        ) : Project

        data class Ungrouped(
            override val id: String,
            override val expanded: Boolean,
            override val sessions: List<SessionItem>,
            override val nextSessionCursor: PageCursor<SessionId>? = null,
        ) : Project {
            override val name: String get() = Chat.UngroupedProjectKey
        }
    }

    internal data class SessionItem(
        val id: String,
        val title: String,
        val status: NavigationStatus?,
        val expanded: Boolean,
        val children: List<SessionItem>,
        val nextChildCursor: PageCursor<SessionId>? = null,
    )

    internal fun applyTo(
        screen: AiStudioScreenState,
        chats: Sequence<Chat>,
    ): AiStudioScreenState {
        val text = query.trim()
        return screen.copy(
            chatSearch = query,
            chatSearchVisible = searchVisible,
            chatsVisible = visible,
            chats = chats.filter { it.matchesSessionSearch(text) }.toList(),
            navigation = rows(),
            usesPagedNavigation = true,
            deletionError = deletionError,
            renameError = renameError,
        )
    }

    public fun rows(): List<NavigationRow> = buildList {
        for (project in projects) addProject(project)
        addPageRow("more-projects", NavigationKind.MoreProjects, "", nextProjectCursor != null)
    }

    private fun MutableList<NavigationRow>.addProject(project: Project) {
        val kind = when (project) {
            is Project.Named -> NavigationKind.Project
            is Project.Ungrouped -> NavigationKind.Section
        }
        val projectKey = project.id.removePrefix("project:")
        add(
            NavigationRow(
                id = project.id,
                title = project.name,
                kind = kind,
                project = projectKey,
                expanded = project.expanded,
            ),
        )
        if (!project.expanded) return

        for (session in project.sessions) addSession(session, projectKey)
        addPageRow("more:$projectKey", NavigationKind.More, projectKey, project.nextSessionCursor != null)
    }

    private fun MutableList<NavigationRow>.addSession(
        session: SessionItem,
        project: String,
        depth: Int = 0,
        lastChild: Boolean = false,
    ) {
        val pageRowId = "more-children:${session.id}"
        val expandable = session.children.isNotEmpty() || session.nextChildCursor != null || pageRowId in pages.failures
        add(
            NavigationRow(
                id = session.id,
                title = session.title,
                kind = NavigationKind.Chat,
                project = project,
                depth = depth,
                expanded = session.expanded.takeIf { expandable },
                selected = session.id == selectedSessionId,
                status = session.status,
                lastChild = lastChild,
            ),
        )
        if (!session.expanded) return

        session.children.forEachIndexed { index, child ->
            addSession(child, project, depth + 1, index == session.children.lastIndex)
        }
        addPageRow(pageRowId, NavigationKind.MoreChildren, session.id, session.nextChildCursor != null, depth + 1)
    }

    private fun MutableList<NavigationRow>.addPageRow(
        id: String,
        kind: NavigationKind,
        ownerId: String,
        hasNextPage: Boolean,
        depth: Int = 0,
    ) {
        val failure = pages.failures[id]
        if (!hasNextPage && failure == null) return
        add(NavigationRow(
            id, "", kind, ownerId, depth,
            page = (pages.loadedPageCounts[id] ?: 0) + 1,
            loadError = failure?.let { NavigationLoadError(it.diagnostic, it.request.isFirstPage, id == retryingPage) },
        ))
    }
}

internal fun Chat.matchesSessionSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) || project.contains(query, ignoreCase = true)
