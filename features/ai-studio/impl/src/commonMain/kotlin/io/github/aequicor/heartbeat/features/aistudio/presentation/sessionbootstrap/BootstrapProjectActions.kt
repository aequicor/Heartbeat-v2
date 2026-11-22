package io.github.aequicor.heartbeat.features.aistudio.presentation.sessionbootstrap

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction.BootstrapProject
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.SessionBootstrapState
import okio.Path.Companion.toPath

internal suspend fun applyBootstrapProjectAction(
    bootstrap: SessionBootstrapInteractor,
    state: SessionBootstrapState,
    action: BootstrapProject,
): SessionBootstrapState = when (action) {
    is BootstrapProject.Add -> createProject(bootstrap, state, action.workingDirectoryPath)
    is BootstrapProject.Retry -> state.pendingProject?.let {
        createProject(bootstrap, state, it.workingDirectoryPath, action.name)
    } ?: state
    BootstrapProject.Cancel -> state.copy(error = null, pendingProject = null)
}

private suspend fun createProject(
    bootstrap: SessionBootstrapInteractor,
    state: SessionBootstrapState,
    directory: String,
    name: String? = null,
): SessionBootstrapState = try {
    val path = directory.toPath(normalize = true)
    val result = if (name == null) bootstrap.createProject(path) else bootstrap.registerProject(name, path)
    when (result) {
        is ProjectCreationResult.Created ->
            SessionBootstrapState(SessionBootstrapState.Project.Existing(result.project.id.value.toString()))
        is ProjectCreationResult.NameAlreadyExists -> state.copy(
            error = SessionBootstrapState.Error.ProjectNameTaken(result.immutableName),
            pendingProject = SessionBootstrapState.PendingProject(directory, result.immutableName),
        )
        ProjectCreationResult.InvalidWorkingDirectory ->
            state.copy(error = SessionBootstrapState.Error.InvalidWorkingDirectory)
        ProjectCreationResult.InvalidName -> state.copy(error = SessionBootstrapState.Error.InvalidName)
    }
} catch (_: IllegalArgumentException) {
    state.copy(error = SessionBootstrapState.Error.InvalidWorkingDirectory)
} catch (_: StudioRepositoryException) {
    state.copy(error = SessionBootstrapState.Error.StorageFailure)
}
