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

internal suspend fun PagesDraft.loadMoreRoots(id: ProjectId, cursor: PageCursor<SessionId>): Boolean {
    val before = roots[id]?.takeIf { it.nextCursor == cursor } ?: return false
    val next = readPage(NavigationPageRequest.Sessions(id, cursor)) {
        catalog.listRootSessions(id, SessionStatus.ACTIVE, after = cursor)
    }
    if (next != null) {
        val merged = before.followedBy(next, StudioSession::id)
        roots[id] = merged.copy(items = merged.items.sortedWith(sessionOrder))
        loadExpandedChildren(next.items)
    }
    return next != null
}

internal suspend fun PagesDraft.loadMoreChildren(id: SessionId, cursor: PageCursor<SessionId>): Boolean {
    val before = children[id]?.takeIf { it.nextCursor == cursor } ?: return false
    val next = readPage(NavigationPageRequest.Children(id, cursor)) {
        catalog.listChildSessions(id, SessionStatus.ACTIVE, after = cursor)
    }
    if (next != null) {
        children[id] = before.followedBy(next, StudioSession::id)
        loadExpandedChildren(next.items)
    }
    return next != null
}

internal suspend fun PagesDraft.loadRoots(id: ProjectId) {
    val page = readPage(NavigationPageRequest.Sessions(id)) { catalog.listRootSessions(id, SessionStatus.ACTIVE) }
        ?: return
    roots[id] = page
    loadExpandedChildren(page.items)
}

internal suspend fun PagesDraft.loadChildren(id: SessionId) {
    val page = children[id] ?: readChildPage(id) ?: return
    children[id] = page
    loadExpandedChildren(page.items)
}

private suspend fun PagesDraft.loadExpandedChildren(rows: List<StudioSession>) {
    for (session in rows) {
        if (session.id in children) continue
        if (session.isExpanded) {
            loadChildren(session.id)
        } else {
            val page = readChildPage(session.id)
            if (page != null) children[session.id] = page
        }
    }
}

private suspend fun PagesDraft.readChildPage(id: SessionId): Page<StudioSession, SessionId>? =
    readPage(NavigationPageRequest.Children(id)) { catalog.listChildSessions(id, SessionStatus.ACTIVE) }

private val sessionOrder = compareByDescending<StudioSession> { it.updatedAt }.thenBy { it.id.value.toString() }

private fun Page<StudioSession, SessionId>.withCreatedSession(session: StudioSession): Page<StudioSession, SessionId> =
    copy(items = (listOf(session) + items).distinctBy(StudioSession::id).sortedWith(sessionOrder))
