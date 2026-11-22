package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectsSessionsRepository
import kotlin.time.Instant

@Inject
internal class SessionBootstrapInteractorImpl(
    private val repository: ProjectsSessionsRepository,
) : SessionBootstrapInteractor {

    override suspend fun registerProject(directory: ProjectDirectory, name: String?): ProjectCreationResult {
        val path = (directory as? ProjectDirectory.Local)?.path
        val projectName = (name ?: path?.name).orEmpty().trim()
        return when {
            path != null && !isValidName(path.name) -> ProjectCreationResult.InvalidWorkingDirectory
            !isValidName(projectName) -> ProjectCreationResult.InvalidName
            else -> repository.registerProject(projectName, directory)
        }
    }

    override suspend fun createSession(
        projectId: ProjectId,
        title: String,
        createdAt: Instant,
    ): SessionBootstrapResult {
        val created = repository.createSessionInActiveProject(projectId, title, createdAt)
            ?: return SessionBootstrapResult.ProjectUnavailable
        return SessionBootstrapResult.Created(created.session, created.project)
    }

    private fun isValidName(name: String) = name.isNotBlank() && name != "." && name != ".."
}
