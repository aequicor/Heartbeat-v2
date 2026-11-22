package io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session as StudioSession

internal suspend fun PagesDraft.revealSessionPath(projectId: ProjectId, path: List<SessionId>) {
    val root = path.firstOrNull() ?: return
    revealInPages(root, { roots[projectId] }) { loadMoreRoots(projectId, it) }
    for ((parent, child) in path.zipWithNext()) {
        if (loadedSession(parent)?.isExpanded != true) break
        revealInPages(child, { children[parent] }) { loadMoreChildren(parent, it) }
    }
}

private suspend fun revealInPages(
    id: SessionId,
    currentPage: () -> Page<StudioSession, SessionId>?,
    load: suspend (PageCursor<SessionId>) -> Boolean,
) {
    var page = currentPage()
    while (page != null && page.items.none { it.id == id }) {
        val cursor = page.nextCursor
        page = if (cursor != null && load(cursor)) currentPage() else null
    }
}
