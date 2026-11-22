package io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectTree
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionTree
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project as StudioProject

internal suspend fun PagesDraft.reloadNavigation(query: String, wasSearching: Boolean = false) {
    if (query.isNotBlank()) {
        val matches = catalog.search(query)
        projectPage = Page(items = matches.map(ProjectTree::project), nextCursor = null)
        showSearchResults(matches)
        return
    }

    if (wasSearching) {
        projectPage = Page(items = listOf(), nextCursor = null)
        clearSessionPages()
    }
    projectPage = readFirstProjectPage() ?: return
    clearSessionPages()
    for (project in projectPage.items) {
        if (project.isExpanded) {
            loadRoots(project.id)
        }
    }
}

internal suspend fun PagesDraft.revealSelection(projectId: ProjectId, path: List<SessionId>) {
    while (projectPage.items.none { it.id == projectId } && projectPage.nextCursor != null) {
        if (!loadMoreProjects()) break
    }
    if (projectPage.items.any { it.id == projectId && it.isExpanded }) {
        revealSessionPath(projectId, path)
    }
}

internal suspend fun PagesDraft.loadMoreProjects(): Boolean {
    val added = appendProjects() ?: return false
    for (project in added) if (project.isExpanded) loadRoots(project.id)
    return true
}

internal suspend fun PagesDraft.reloadProjects() {
    val loaded = projectPage.items.size
    projectPage = readFirstProjectPage() ?: return
    while (projectPage.items.size < loaded && projectPage.nextCursor != null) {
        if (appendProjects() == null) break
    }
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

private suspend fun PagesDraft.appendProjects(): List<StudioProject>? {
    val after = projectPage.nextCursor ?: return null
    return readPage(NavigationPageRequest.Projects(after)) {
        catalog.listProjects(ProjectStatus.ACTIVE, after = after)
    }?.also { next ->
        projectPage = projectPage.followedBy(next, StudioProject::id)
    }?.items
}

private suspend fun PagesDraft.readFirstProjectPage(): Page<StudioProject, ProjectId>? =
    readPage(NavigationPageRequest.Projects()) { catalog.listProjects(ProjectStatus.ACTIVE) }

private fun PagesDraft.clearSessionPages() {
    roots.clear()
    children.clear()
    failures.clear()
    loadedPageCounts.keys.retainAll(setOf("more-projects"))
}

private fun PagesDraft.showSearchResults(matches: List<ProjectTree>) {
    clearSessionPages()
    loadedPageCounts.clear()
    for ((project, rootNodes) in matches) {
        roots[project.id] = Page(items = rootNodes.map(SessionTree::session), nextCursor = null)
        rootNodes.forEach { showSearchBranch(it) }
    }
}

private fun PagesDraft.showSearchBranch(node: SessionTree) {
    children[node.session.id] = Page(items = node.children.map(SessionTree::session), nextCursor = null)
    node.children.forEach { showSearchBranch(it) }
}
