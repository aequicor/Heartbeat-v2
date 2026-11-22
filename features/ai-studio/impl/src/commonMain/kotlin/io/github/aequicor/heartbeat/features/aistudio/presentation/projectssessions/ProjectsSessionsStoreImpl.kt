package io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectsSessionsChange
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.DeletionError
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.RenameError
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Panel
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Project
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Session
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project as StudioProject
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session as StudioSession

internal class ProjectsSessionsStoreImpl(
    private val catalog: ProjectsSessionsInteractor,
    changes: Flow<ProjectsSessionsChange>,
    scope: CoroutineScope,
) : ProjectsSessionsStore {

    private val mutableState = MutableStateFlow(ProjectsSessionsState())
    override val state = mutableState.asStateFlow()
    private val dispatchLock = Mutex()
    private val requests = Channel<Request>(Channel.UNLIMITED, onUndeliveredElement = { it.result.cancel() })

    private data class Request(
        val action: ProjectsSessionsAction,
        val pages: ProjectsSessionsPages,
        val result: CompletableDeferred<Result<Unit>>,
    )

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            changes.collect(::reflectCatalogChange)
        }
        scope.launch {
            for (request in requests) {
                try {
                    if (request.result.isActive) request.result.complete(handle(request))
                } finally {
                    request.result.cancel()
                }
            }
        }.invokeOnCompletion { requests.cancel() }
    }

    private suspend fun reflectCatalogChange(change: ProjectsSessionsChange) = dispatchLock.withLock {
        val current = state.value
        val pages = PagesDraft(current.pages, catalog)
        try {
            if (current.query.isNotBlank()) {
                pages.reloadNavigation(current.query)
            } else {
                when (change) {
                    is ProjectsSessionsChange.ProjectAdded -> pages.reloadProjects()
                    is ProjectsSessionsChange.SessionAdded -> {
                        catalog.findSession(change.session.id)?.let { pages.includeCreatedSession(it) }
                    }
                }
            }
            mutableState.value = pages.applyTo(current).copy(pages = pages.snapshot())
        } catch (_: StudioRepositoryException) {
            // Keep the current list when refreshing search results fails.
        }
    }

    override suspend fun dispatch(action: ProjectsSessionsAction): Result<Unit> {
        if (action is Panel.RetryPage && state.value.retryingPage == action.rowId) return Result.success(Unit)
        val result = CompletableDeferred<Result<Unit>>()
        try {
            requests.send(Request(action, state.value.pages, result))
            return result.await()
        } finally {
            result.cancel()
        }
    }

    private suspend fun handle(request: Request): Result<Unit> = dispatchLock.withLock {
        val current = state.value
        val pages = PagesDraft(current.pages, catalog)
        try {
            val next = when (val action = request.action) {
                is Panel -> pages.updatePanel(action, current, request.pages)
                is Project -> pages.updateProject(action, current, request.pages)
                is Session -> pages.updateSession(action, current, request.pages)
            }
            mutableState.value = pages.applyTo(next).copy(pages = pages.snapshot(), storageError = false)
            Result.success(Unit)
        } catch (failure: StudioRepositoryException) {
            mutableState.value = current.copy(storageError = true)
            Result.failure(failure)
        }
    }

    private suspend fun PagesDraft.selectSession(
        id: SessionId,
        current: ProjectsSessionsState,
    ): ProjectsSessionsState {
        val selected = catalog.findSession(id)?.takeIf { it.status == SessionStatus.ACTIVE } ?: return current
        if (loadedSession(id) == null && current.query.isBlank() && !includeIfInLoadedRange(selected)) {
            val path = mutableSetOf(selected.id)
            var parentId = selected.parentSessionId
            while (parentId != null && path.add(parentId)) {
                val parent = catalog.findSession(parentId)
                if (parent?.status != SessionStatus.ACTIVE) {
                    path.remove(parentId)
                    break
                }
                parentId = parent.parentSessionId
            }
            revealSelection(selected.projectId, path.toList().asReversed())
        }
        return current.copy(selectedSessionId = id.value.toString())
    }

    private suspend fun PagesDraft.projectForAction(id: ProjectId): StudioProject? =
        loadedProject(id) ?: findCatalogProject(catalog, id)

    private suspend fun PagesDraft.updateProject(
        action: Project,
        current: ProjectsSessionsState,
        requested: ProjectsSessionsPages,
    ): ProjectsSessionsState = when (action) {
        is Project.SetExpanded -> {
            val id = runCatching { ProjectId.parse(action.id) }.getOrNull()
            id?.let { projectForAction(it) }?.let { setProjectExpanded(it, action.isExpanded) }
            current
        }
        is Project.LoadMoreSessions -> {
            val id = runCatching { ProjectId.parse(action.projectId) }.getOrNull()
            if (id != null) requested.roots[id]?.nextCursor?.let { loadMoreRoots(id, it) }
            current
        }
        is Project.Rename -> {
            val error = renameInStorage(action.title, RenameError.Project) {
                projectForAction(ProjectId.parse(action.id))?.let { catalog.renameProject(it, action.title) }
            }
            if (error == null) {
                if (current.query.isBlank()) reloadProjects()
                else reloadNavigation(current.query)
            }
            current.copy(deletionError = null, renameError = error)
        }
        is Project.Delete -> {
            val error = deleteFromStorage(DeletionError.Project, catalog) {
                projectForAction(ProjectId.parse(action.id))?.let { deleteProject(it) }
            }
            val next = current.copy(deletionError = error, renameError = null)
            if (error == null) reloadAfterRemoval(next) else next
        }
    }

    private suspend fun PagesDraft.updateSession(
        action: Session,
        current: ProjectsSessionsState,
        requested: ProjectsSessionsPages,
    ): ProjectsSessionsState = when (action) {
        is Session.SetExpanded -> {
            val id = runCatching { SessionId.parse(action.id) }.getOrNull()
            if (id != null) setSessionExpanded(id, action.isExpanded)
            current
        }
        is Session.LoadMoreChildren -> {
            val id = runCatching { SessionId.parse(action.parentId) }.getOrNull()
            if (id != null) requested.children[id]?.nextCursor?.let { loadMoreChildren(id, it) }
            current
        }
        is Session.Select -> {
            val id = runCatching { SessionId.parse(action.id) }.getOrNull()
            if (id == null) current else selectSession(id, current)
        }
        is Session.Archive -> {
            catalog.findSession(SessionId.parse(action.id))?.let {
                catalog.setSessionArchived(it, isArchived = true, changedAt = Clock.System.now())
            }
            reloadAfterRemoval(current)
        }
        is Session.Rename -> {
            val error = renameSession(action)
            if (error == null && current.query.isNotBlank()) reloadNavigation(current.query)
            current.copy(deletionError = null, renameError = error)
        }
        is Session.Delete -> deleteSession(action.id, current)
    }

    private suspend fun PagesDraft.deleteSession(id: String, current: ProjectsSessionsState): ProjectsSessionsState {
        val error = deleteFromStorage(DeletionError.Session, catalog) {
            findSession(SessionId.parse(id))?.let { deleteSession(it) }
        }
        val next = current.copy(deletionError = error, renameError = null)
        return if (error == null) reloadAfterRemoval(next) else next
    }

    private suspend fun PagesDraft.renameSession(action: Session.Rename): RenameError? =
        renameInStorage(action.title, RenameError.Session) {
            catalog.findSession(SessionId.parse(action.id))?.let {
                catalog.renameSession(it, action.title)
                updateSession(it.copy(title = action.title))
            }
        }

    private suspend fun PagesDraft.updatePanel(
        action: Panel,
        current: ProjectsSessionsState,
        requested: ProjectsSessionsPages,
    ): ProjectsSessionsState = when (action) {
        Panel.Load -> {
            reloadNavigation(current.query)
            current.copy(visible = true)
        }
        is Panel.Search -> {
            val wasSearching = current.query.isNotBlank()
            if (wasSearching || action.query.isNotBlank()) reloadNavigation(action.query, wasSearching)
            current.copy(query = action.query)
        }
        Panel.ExpandSearch -> current.copy(searchVisible = true)
        Panel.CollapseSearch -> {
            if (current.query.isNotBlank()) reloadNavigation("", wasSearching = true)
            current.copy(searchVisible = false, query = "")
        }
        Panel.ExpandPanel -> current.copy(visible = true)
        Panel.CollapsePanel -> current.copy(visible = false)
        Panel.LoadMoreProjects -> {
            val cursor = requested.projectPage.nextCursor
            if (cursor != null && projectPage.nextCursor == cursor) loadMoreProjects()
            current
        }
        is Panel.RetryPage -> {
            retryFailedPage(action.rowId, requested.failures[action.rowId]) {
                mutableState.value = current.copy(retryingPage = action.rowId)
            }
            current
        }
    }

    private suspend fun PagesDraft.reloadAfterRemoval(state: ProjectsSessionsState): ProjectsSessionsState {
        reloadNavigation(state.query)
        val selectedId = state.selectedSessionId ?: return state
        return if (catalog.findSession(SessionId.parse(selectedId))?.status == SessionStatus.ACTIVE) state
        else state.copy(selectedSessionId = null)
    }
}

internal suspend fun findCatalogProject(catalog: ProjectsSessionsInteractor, id: ProjectId): StudioProject? {
    for (status in ProjectStatus.entries) {
        var cursor: PageCursor<ProjectId>? = null
        do {
            val page = catalog.listProjects(status, after = cursor)
            val project = page.items.firstOrNull { it.id == id }
            if (project != null) return project
            cursor = page.nextCursor
        } while (cursor != null)
    }
    return null
}

private suspend fun deleteFromStorage(
    failure: DeletionError,
    catalog: ProjectsSessionsInteractor,
    delete: suspend ProjectsSessionsInteractor.() -> Unit,
): DeletionError? {
    return try {
        catalog.delete()
        null
    } catch (_: IllegalArgumentException) {
        failure
    } catch (_: StudioRepositoryException) {
        failure
    }
}

private suspend fun renameInStorage(
    title: String,
    failure: RenameError,
    rename: suspend () -> Unit?,
): RenameError? = try {
    if (title.isBlank() || rename() == null) failure else null
} catch (_: IllegalArgumentException) {
    failure
} catch (_: StudioRepositoryException) {
    failure
}
