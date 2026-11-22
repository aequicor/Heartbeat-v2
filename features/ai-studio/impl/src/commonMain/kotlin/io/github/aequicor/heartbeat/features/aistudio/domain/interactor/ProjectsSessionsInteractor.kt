package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

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

@Suppress("TooManyFunctions") // The catalog exposes operations for both projects and sessions.
internal interface ProjectsSessionsInteractor {

    suspend fun createProject(workingDirectory: Path, name: String = workingDirectory.name): ProjectCreationResult

    suspend fun search(query: String): List<ProjectTree>

    suspend fun listProjects(
        status: ProjectStatus,
        limit: Int = ProjectPageSize,
        after: PageCursor<ProjectId>? = null,
    ): Page<Project, ProjectId>

    suspend fun renameProject(project: Project, title: String)

    suspend fun setProjectExpanded(project: Project, isExpanded: Boolean)

    suspend fun setProjectArchived(project: Project, isArchived: Boolean)

    suspend fun deleteProject(project: Project)

    suspend fun findSession(id: SessionId): Session?

    suspend fun listRootSessions(
        projectId: ProjectId,
        status: SessionStatus,
        limit: Int = SessionPageSize,
        after: PageCursor<SessionId>? = null,
    ): Page<Session, SessionId>

    suspend fun listChildSessions(
        parentSessionId: SessionId,
        status: SessionStatus,
        limit: Int = SessionPageSize,
        after: PageCursor<SessionId>? = null,
    ): Page<Session, SessionId>

    suspend fun renameSession(session: Session, title: String)

    suspend fun setSessionExpanded(session: Session, isExpanded: Boolean)

    suspend fun setSessionArchived(session: Session, isArchived: Boolean, changedAt: Instant)

    suspend fun deleteSession(session: Session)

    companion object {
        private const val ProjectPageSize = 20
        const val SessionPageSize = 5
    }
}
