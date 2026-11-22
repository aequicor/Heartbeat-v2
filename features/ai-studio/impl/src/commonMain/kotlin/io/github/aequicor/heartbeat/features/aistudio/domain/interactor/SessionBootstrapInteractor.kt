package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import kotlin.time.Clock
import kotlin.time.Instant

internal interface SessionBootstrapInteractor {

    suspend fun registerProject(directory: ProjectDirectory, name: String? = null): ProjectCreationResult

    suspend fun createSession(
        projectId: ProjectId,
        title: String,
        createdAt: Instant = Clock.System.now(),
    ): SessionBootstrapResult
}
