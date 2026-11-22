package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface SessionBootstrapInteractor {

    @Throwable(CancellationException::class)
    suspend fun withDraft(projectId: ProjectId, use: suspend (ChatCreationDraft) -> Unit): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun registerProject(directory: ProjectDirectory, name: String? = null): Result<ProjectCreationResult>
}
