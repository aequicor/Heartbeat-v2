package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import okio.Path
import kotlin.time.Clock
import kotlin.time.Instant

internal interface SessionBootstrapInteractor {

    suspend fun createProject(directory: Path): ProjectCreationResult

    suspend fun registerProject(name: String, directory: Path): ProjectCreationResult

    suspend fun createSession(
        projectId: ProjectId,
        title: String,
        createdAt: Instant = Clock.System.now(),
        parentSessionId: SessionId? = null,
    ): Session

    suspend fun startInProject(projectId: ProjectId, initialPrompt: String): SessionBootstrapResult
}
