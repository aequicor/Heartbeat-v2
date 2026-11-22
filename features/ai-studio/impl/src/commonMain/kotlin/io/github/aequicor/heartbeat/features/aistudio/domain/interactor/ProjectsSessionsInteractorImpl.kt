package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectsSessionsSearchDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectTree
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import okio.Path
import kotlin.time.Instant

@Suppress("TooManyFunctions")
@Inject
internal class ProjectsSessionsInteractorImpl(
    private val projects: ProjectDataSource,
    private val sessions: SessionsDataSource,
    private val catalogSearch: ProjectsSessionsSearchDataSource,
) : ProjectsSessionsInteractor {

    override suspend fun createProject(workingDirectory: Path, name: String): ProjectCreationResult =
        projects.create(name, workingDirectory)

    override suspend fun search(query: String): List<ProjectTree> {
        val text = query.trim()
        return if (text.isEmpty()) listOf() else catalogSearch.search(text)
    }

    override suspend fun listProjects(
        status: ProjectStatus,
        limit: Int,
        after: PageCursor<ProjectId>?,
    ): Page<Project, ProjectId> = projects.list(status, limit, after)

    override suspend fun renameProject(project: Project, title: String) {
        requireUserProject(project.id)
        projects.renameTitle(project.id, title)
    }

    override suspend fun setProjectExpanded(project: Project, isExpanded: Boolean) =
        projects.setExpanded(project.id, isExpanded)

    override suspend fun setProjectArchived(project: Project, isArchived: Boolean) {
        requireUserProject(project.id)
        val status = if (isArchived) ProjectStatus.ARCHIVED else ProjectStatus.ACTIVE
        projects.setStatus(project.id, status)
    }

    override suspend fun deleteProject(project: Project) {
        requireUserProject(project.id)
        projects.delete(project.id)
    }

    override suspend fun findSession(id: SessionId): Session? = sessions.findById(id)

    override suspend fun listRootSessions(
        projectId: ProjectId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<SessionId>?,
    ): Page<Session, SessionId> = sessions.listRoots(projectId, status, limit, after)

    override suspend fun listChildSessions(
        parentSessionId: SessionId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<SessionId>?,
    ): Page<Session, SessionId> = sessions.listChildren(parentSessionId, status, limit, after)

    override suspend fun renameSession(session: Session, title: String) = sessions.rename(session.id, title)

    override suspend fun setSessionExpanded(session: Session, isExpanded: Boolean) =
        sessions.setExpanded(session.id, isExpanded)

    override suspend fun setSessionArchived(session: Session, isArchived: Boolean, changedAt: Instant) {
        val status = if (isArchived) SessionStatus.ARCHIVED else SessionStatus.ACTIVE
        sessions.setStatus(session.id, status, changedAt)
    }

    override suspend fun deleteSession(session: Session) = sessions.delete(session.id)

    private fun requireUserProject(id: ProjectId) {
        require(id != ProjectId.Ungrouped) { "The system project cannot be renamed, archived or deleted" }
    }
}
