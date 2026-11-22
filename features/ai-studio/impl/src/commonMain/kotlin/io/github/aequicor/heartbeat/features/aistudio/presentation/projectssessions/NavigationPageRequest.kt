package io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId

internal sealed interface NavigationPageRequest {
    val rowId: String
    val isFirstPage: Boolean

    data class Projects(val after: PageCursor<ProjectId>? = null) : NavigationPageRequest {
        override val rowId get() = "more-projects"
        override val isFirstPage get() = after == null
    }

    data class Sessions(val projectId: ProjectId, val after: PageCursor<SessionId>? = null) : NavigationPageRequest {
        override val rowId get() = "more:${projectId.value}"
        override val isFirstPage get() = after == null
    }

    data class Children(val parentId: SessionId, val after: PageCursor<SessionId>? = null) : NavigationPageRequest {
        override val rowId get() = "more-children:${parentId.value}"
        override val isFirstPage get() = after == null
    }
}

internal class NavigationPageFailure(val request: NavigationPageRequest, val diagnostic: String)

internal suspend fun PagesDraft.retryFailedPage(
    rowId: String,
    requested: NavigationPageFailure?,
    onRetrying: () -> Unit,
) {
    val failure = failures[rowId]?.takeIf { it === requested } ?: return
    onRetrying()
    failures.remove(rowId)
    when (val request = failure.request) {
        is NavigationPageRequest.Projects -> {
            if (request.after == null) reloadProjects()
            else if (request.after == projectPage.nextCursor) loadMoreProjects()
        }
        is NavigationPageRequest.Sessions -> {
            if (request.after == null) loadRoots(request.projectId)
            else loadMoreRoots(request.projectId, request.after)
        }
        is NavigationPageRequest.Children -> {
            if (request.after == null) loadChildren(request.parentId)
            else loadMoreChildren(request.parentId, request.after)
        }
    }
}

internal suspend fun <T> PagesDraft.readPage(request: NavigationPageRequest, read: suspend () -> T): T? {
    val rowId = request.rowId
    if (failures[rowId]?.request == request) return null
    val number = if (request.isFirstPage) 1 else (loadedPageCounts[rowId] ?: 1) + 1
    return try {
        val page = read()
        failures.remove(rowId)
        loadedPageCounts[rowId] = number
        page
    } catch (failure: StudioRepositoryException) {
        val scope = when (request) {
            is NavigationPageRequest.Projects -> "projects"
            is NavigationPageRequest.Sessions -> "sessions project=${request.projectId.value}"
            is NavigationPageRequest.Children -> {
                val projectId = loadedSession(request.parentId)?.projectId?.value
                "child sessions project=$projectId parent=${request.parentId.value}"
            }
        }
        val category = failure.cause?.let { it::class.simpleName } ?: "StudioRepositoryException"
        failures[rowId] = NavigationPageFailure(request, "$scope\npage=$number\nerror=$category")
        null
    }
}
