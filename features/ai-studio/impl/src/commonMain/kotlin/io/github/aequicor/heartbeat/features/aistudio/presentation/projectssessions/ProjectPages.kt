package io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectTree
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionTree
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project as StudioProject

internal suspend fun PagesDraft.reloadNavigation(query: String) {
    if (query.isNotBlank()) {
        val matches = catalog.search(query)
        projectPage = Page(items = matches.map(ProjectTree::project), nextCursor = null)
        showSearchResults(matches)
        return
    }

    projectPage = catalog.listProjects(ProjectStatus.ACTIVE)
    clearSessionPages()
    for (project in projectPage.items) {
        if (project.isExpanded) {
            loadRoots(project.id)
        }
    }
}

internal suspend fun PagesDraft.revealSelection(projectId: ProjectId, path: List<SessionId>) {
    while (projectPage.items.none { it.id == projectId } && projectPage.nextCursor != null) loadMoreProjects()
    if (projectPage.items.any { it.id == projectId && it.isExpanded }) {
        revealSessionPath(projectId, path)
    }
}

internal suspend fun PagesDraft.loadMoreProjects() {
    val added = appendProjects()
    for (project in added) if (project.isExpanded) loadRoots(project.id)
}

internal suspend fun PagesDraft.reloadProjects() {
    val loaded = projectPage.items.size
    projectPage = catalog.listProjects(ProjectStatus.ACTIVE)
    while (projectPage.items.size < loaded && projectPage.nextCursor != null) appendProjects()
    for (project in projectPage.items) {
        if (project.isExpanded && project.id !in roots) loadRoots(project.id)
    }
}

internal suspend fun PagesDraft.setProjectExpanded(target: StudioProject, isExpanded: Boolean) {
    val id = target.id
    catalog.setProjectExpanded(target, isExpanded)
    val updatedProjects = projectPage.items.map { project ->
        if (project.id != id) return@map project
        when (project) {
            is StudioProject.WithoutDirectory -> project.copy(isExpanded = isExpanded)
            is StudioProject.WithDirectory -> project.copy(isExpanded = isExpanded)
        }
    }
    projectPage = projectPage.copy(items = updatedProjects)
    if (isExpanded && id !in roots) loadRoots(id)
}

private suspend fun PagesDraft.appendProjects(): List<StudioProject> {
    val after = projectPage.nextCursor ?: return listOf()
    val next = catalog.listProjects(ProjectStatus.ACTIVE, after = after)
    projectPage = projectPage.followedBy(next, StudioProject::id)
    return next.items
}

private fun PagesDraft.clearSessionPages() {
    roots.clear()
    children.clear()
}

private fun PagesDraft.showSearchResults(matches: List<ProjectTree>) {
    clearSessionPages()
    for ((project, rootNodes) in matches) {
        roots[project.id] = Page(items = rootNodes.map(SessionTree::session), nextCursor = null)
        rootNodes.forEach { showSearchBranch(it) }
    }
}

private fun PagesDraft.showSearchBranch(node: SessionTree) {
    children[node.session.id] = Page(items = node.children.map(SessionTree::session), nextCursor = null)
    node.children.forEach { showSearchBranch(it) }
}
