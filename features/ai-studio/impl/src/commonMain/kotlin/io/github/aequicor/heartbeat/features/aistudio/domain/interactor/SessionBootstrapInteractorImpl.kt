package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectsSessionsRepository
import okio.Path
import kotlin.time.Instant

@Inject
internal class SessionBootstrapInteractorImpl(
    private val repository: ProjectsSessionsRepository,
) : SessionBootstrapInteractor {

    override suspend fun createProject(directory: Path): ProjectCreationResult =
        registerProject(directory.name, directory)

    override suspend fun registerProject(name: String, directory: Path): ProjectCreationResult {
        val projectName = name.trim()
        return when {
            !isValidName(directory.name) -> ProjectCreationResult.InvalidWorkingDirectory
            !isValidName(projectName) -> ProjectCreationResult.InvalidName
            else -> repository.registerProject(projectName, directory)
        }
    }

    override suspend fun createSession(
        projectId: ProjectId,
        title: String,
        createdAt: Instant,
        parentSessionId: SessionId?,
    ): Session = repository.createSession(projectId, title, createdAt, parentSessionId)

    override suspend fun startInProject(projectId: ProjectId, initialPrompt: String): SessionBootstrapResult {
        val prompt = initialPrompt.trim()
        if (prompt.isEmpty()) return SessionBootstrapResult.EmptyPrompt

        val title = prompt.lineSequence().first().take(MaxTitleLength)
        val created = repository.createSessionInActiveProject(projectId, title)
        return if (created == null) SessionBootstrapResult.ProjectUnavailable
        else SessionBootstrapResult.Created(created.session, created.project)
    }

    private fun isValidName(name: String) = name.isNotBlank() && name != "." && name != ".."

    private companion object {
        const val MaxTitleLength = 48
    }
}
