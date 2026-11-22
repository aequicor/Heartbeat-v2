package io.github.aequicor.heartbeat.features.aistudio.presentation.sessionbootstrap

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.SessionBootstrapState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val MaxTitleLength = 48

internal fun sessionTitleFromPrompt(prompt: String): String =
    prompt.lineSequence().firstOrNull().orEmpty().take(MaxTitleLength)

internal suspend fun submitSessionBootstrap(
    screen: MutableStateFlow<AiStudioScreenState>,
    interactor: SessionBootstrapInteractor?,
    action: AiStudioAction.SubmitSessionBootstrap,
    onCreated: suspend (AiStudioAction.SubmitSessionBootstrap, SessionBootstrapResult.Created) -> Unit,
) {
    val bootstrap = screen.value.sessionBootstrap ?: return
    val prompt = action.prompt.trim()
    if (prompt.isEmpty()) {
        screen.update {
            it.copy(
                sessionBootstrap = bootstrap.copy(
                    project = action.project, error = SessionBootstrapState.Error.EmptyPrompt,
                ),
            )
        }
        return
    }
    screen.update {
        it.copy(sessionBootstrap = bootstrap.copy(operation = SessionBootstrapState.Operation.CreatingSession))
    }
    when (val result = createSession(interactor, action.project, prompt)) {
        is SessionBootstrapResult.Created -> {
            screen.update { it.copy(sessionBootstrap = null) }
            onCreated(action, result)
        }
        SessionBootstrapResult.ProjectUnavailable, null -> {
            val error = if (result == null) SessionBootstrapState.Error.StorageFailure
            else SessionBootstrapState.Error.ProjectUnavailable
            screen.update { it.copy(sessionBootstrap = bootstrap.copy(project = action.project, error = error)) }
        }
    }
}

private suspend fun createSession(
    interactor: SessionBootstrapInteractor?,
    project: SessionBootstrapState.Project,
    prompt: String,
): SessionBootstrapResult? = try {
    val projectId = when (project) {
        SessionBootstrapState.Project.Ungrouped -> ProjectId.Ungrouped
        is SessionBootstrapState.Project.Existing -> ProjectId.parse(project.id)
    }
    interactor?.createSession(projectId, sessionTitleFromPrompt(prompt))
} catch (_: IllegalArgumentException) {
    SessionBootstrapResult.ProjectUnavailable
} catch (_: StudioRepositoryException) {
    null
}
