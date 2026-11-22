package io.github.aequicor.heartbeat.features.aistudio.domain.repository

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectsSessionsChange
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.time.Clock
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
internal class ProjectsSessionsRepository(
    private val projectDataSource: ProjectDataSource,
    private val sessionsDataSource: SessionsDataSource,
) {

    private val changes = MutableSharedFlow<ProjectsSessionsChange>(replay = 1)

    fun observeChanges(): SharedFlow<ProjectsSessionsChange> = changes.asSharedFlow()

    suspend fun registerProject(name: String, directory: Path): ProjectCreationResult {
        val result = projectDataSource.create(name, directory)
        if (result is ProjectCreationResult.Created) {
            publish(ProjectsSessionsChange.ProjectAdded(result.project.id))
        }
        return result
    }

    suspend fun createSession(
        projectId: ProjectId,
        title: String,
        createdAt: Instant,
        parentSessionId: SessionId?,
    ): Session {
        val change = sessionsDataSource.transaction {
            val session = sessionsDataSource.create(projectId, title, createdAt, parentSessionId)
            val project = checkNotNull(projectDataSource.findById(session.projectId))
            ProjectsSessionsChange.SessionAdded(session, project)
        }
        publish(change)
        return change.session
    }

    suspend fun createSessionInActiveProject(
        projectId: ProjectId,
        title: String,
    ): ProjectsSessionsChange.SessionAdded? {
        val change = sessionsDataSource.transaction {
            val project = projectDataSource.findById(projectId) ?: return@transaction null
            if (project.status != ProjectStatus.ACTIVE) return@transaction null

            val session = sessionsDataSource.create(projectId, title, Clock.System.now(), parentSessionId = null)
            ProjectsSessionsChange.SessionAdded(session, project)
        } ?: return null

        publish(change)
        return change
    }

    private suspend fun publish(change: ProjectsSessionsChange) {
        withContext(NonCancellable) { changes.emit(change) }
    }
}
