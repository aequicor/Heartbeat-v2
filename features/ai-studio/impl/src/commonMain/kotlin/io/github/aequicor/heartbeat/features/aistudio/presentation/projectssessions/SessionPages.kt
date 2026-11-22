package io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session as StudioSession

internal suspend fun PagesDraft.setSessionExpanded(id: SessionId, isExpanded: Boolean) {
    val session = loadedSession(id) ?: catalog.findSession(id) ?: return
    catalog.setSessionExpanded(session, isExpanded)
    updateSession(session.copy(isExpanded = isExpanded))
    if (isExpanded) loadChildren(id)
}

internal fun PagesDraft.includeCreatedSession(session: StudioSession) {
    if (session.status != SessionStatus.ACTIVE) return
    val parentId = session.parentSessionId
    if (parentId == null) {
        val page = roots[session.projectId]
        if (page != null) roots[session.projectId] = page.withCreatedSession(session)
    } else {
        val page = children[parentId]
        if (page != null) children[parentId] = page.withCreatedSession(session)
    }
}

internal fun PagesDraft.includeIfInLoadedRange(session: StudioSession): Boolean {
    val parentId = session.parentSessionId
    val page = if (parentId == null) roots[session.projectId] else children[parentId]
    val last = page?.items?.lastOrNull()
    val inLoadedRange = page != null &&
        (page.nextCursor == null || last == null || sessionOrder.compare(session, last) <= 0)
    if (inLoadedRange) includeCreatedSession(session)
    return inLoadedRange
}

internal suspend fun PagesDraft.revealSessionPath(projectId: ProjectId, path: List<SessionId>) {
    val root = path.firstOrNull() ?: return
    while (roots[projectId]?.items?.none { it.id == root } == true) {
        val cursor = roots[projectId]?.nextCursor ?: break
        loadMoreRoots(projectId, cursor)
    }
    for ((parent, child) in path.zipWithNext()) {
        if (loadedSession(parent)?.isExpanded != true) break
        while (children[parent]?.items?.none { it.id == child } == true) {
            val cursor = children[parent]?.nextCursor ?: break
            loadMoreChildren(parent, cursor)
        }
    }
}

internal suspend fun PagesDraft.loadMoreRoots(id: ProjectId, cursor: PageCursor<SessionId>) {
    val before = roots[id] ?: return
    if (before.nextCursor != cursor) return
    val next = catalog.listRootSessions(id, SessionStatus.ACTIVE, after = cursor)
    val merged = before.followedBy(next, StudioSession::id)
    roots[id] = merged.copy(items = merged.items.sortedWith(sessionOrder))
    loadExpandedChildren(next.items)
}

internal suspend fun PagesDraft.loadMoreChildren(id: SessionId, cursor: PageCursor<SessionId>) {
    val before = children[id] ?: return
    if (before.nextCursor != cursor) return
    val next = catalog.listChildSessions(id, SessionStatus.ACTIVE, after = cursor)
    children[id] = before.followedBy(next, StudioSession::id)
    loadExpandedChildren(next.items)
}

internal suspend fun PagesDraft.loadRoots(id: ProjectId) {
    val page = catalog.listRootSessions(id, SessionStatus.ACTIVE)
    roots[id] = page
    loadExpandedChildren(page.items)
}

private suspend fun PagesDraft.loadChildren(id: SessionId) {
    val page = children[id] ?: catalog.listChildSessions(id, SessionStatus.ACTIVE)
    children[id] = page
    loadExpandedChildren(page.items)
}

private suspend fun PagesDraft.loadExpandedChildren(rows: List<StudioSession>) {
    for (session in rows) {
        if (session.id in children) continue
        if (session.isExpanded) loadChildren(session.id)
        else children[session.id] = catalog.listChildSessions(session.id, SessionStatus.ACTIVE)
    }
}

private val sessionOrder = compareByDescending<StudioSession> { it.updatedAt }.thenBy { it.id.value.toString() }

private fun Page<StudioSession, SessionId>.withCreatedSession(session: StudioSession): Page<StudioSession, SessionId> =
    copy(items = (listOf(session) + items).distinctBy(StudioSession::id).sortedWith(sessionOrder))
