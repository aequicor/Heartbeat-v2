package io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions

import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.toNavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project as StudioProject
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session as StudioSession

internal data class ProjectsSessionsPages(
    val projectPage: Page<StudioProject, ProjectId> = Page(items = listOf(), nextCursor = null),
    val roots: Map<ProjectId, Page<StudioSession, SessionId>> = mapOf(),
    val children: Map<SessionId, Page<StudioSession, SessionId>> = mapOf(),
    val failures: Map<String, NavigationPageFailure> = mapOf(),
    val loadedPageCounts: Map<String, Int> = mapOf(),
)

internal class PagesDraft(initial: ProjectsSessionsPages, val catalog: ProjectsSessionsInteractor) {
    var projectPage = initial.projectPage
    val roots = initial.roots.toMutableMap()
    val children = initial.children.toMutableMap()
    val failures = initial.failures.toMutableMap()
    val loadedPageCounts = initial.loadedPageCounts.toMutableMap()

    fun snapshot(): ProjectsSessionsPages =
        ProjectsSessionsPages(projectPage, roots.toMap(), children.toMap(), failures.toMap(), loadedPageCounts.toMap())
}

internal fun PagesDraft.loadedProject(id: ProjectId): StudioProject? = projectPage.items.firstOrNull { it.id == id }

internal fun PagesDraft.loadedSession(id: SessionId): StudioSession? =
    (roots.values.asSequence() + children.values.asSequence())
        .flatMap { it.items }
        .firstOrNull { it.id == id }

internal fun PagesDraft.updateSession(session: StudioSession) {
    val rootPage = roots[session.projectId]
    if (rootPage != null && rootPage.items.any { it.id == session.id }) {
        roots[session.projectId] = rootPage.withSession(session)
    } else {
        val parentId = session.parentSessionId ?: return
        val page = children[parentId] ?: return
        children[parentId] = page.withSession(session)
    }
}

internal fun PagesDraft.applyTo(state: ProjectsSessionsState): ProjectsSessionsState {
    val searching = state.query.isNotBlank()
    val projectStates = projectPage.items.map { project ->
        val id = project.id.value.toString()
        val page = roots[project.id]
        val visible = page?.items.orEmpty().map {
            sessionState(it, searching)
        }
        val expanded = project.isExpanded || searching
        when (project) {
            is StudioProject.WithoutDirectory -> ProjectsSessionsState.Project.Ungrouped(
                id = "project:$id",
                expanded = expanded,
                sessions = visible,
                nextSessionCursor = page?.nextCursor,
            )
            is StudioProject.WithDirectory -> ProjectsSessionsState.Project.Named(
                id = "project:$id",
                name = project.title,
                expanded = expanded,
                sessions = visible,
                nextSessionCursor = page?.nextCursor,
            )
        }
    }
    return state.copy(projects = projectStates, nextProjectCursor = projectPage.nextCursor)
}

private fun PagesDraft.sessionState(
    session: StudioSession,
    searching: Boolean,
): ProjectsSessionsState.SessionItem {
    val id = session.id.value.toString()
    val page = children[session.id]
    val visible = page?.items.orEmpty().map {
        sessionState(it, searching)
    }
    return ProjectsSessionsState.SessionItem(
        id = id,
        title = session.title,
        status = session.workState.toNavigationStatus(),
        expanded = searching || session.isExpanded,
        children = visible,
        nextChildCursor = page?.nextCursor,
    )
}

internal fun <Item, Id> Page<Item, Id>.followedBy(next: Page<Item, Id>, id: (Item) -> Id): Page<Item, Id> =
    Page((items + next.items).distinctBy(id), next.nextCursor)

private fun Page<StudioSession, SessionId>.withSession(session: StudioSession): Page<StudioSession, SessionId> =
    copy(items = items.map { if (it.id == session.id) session else it })
