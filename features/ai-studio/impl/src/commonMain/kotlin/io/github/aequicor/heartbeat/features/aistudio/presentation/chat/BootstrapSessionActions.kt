package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Selection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal suspend fun submitSessionBootstrap(
    screen: MutableStateFlow<AiStudioScreenState>,
    picker: BootstrapEnginePicker?,
    action: AiStudioAction.SubmitSessionBootstrap,
    onCreated: suspend (
        AiStudioAction.SubmitSessionBootstrap, SessionBootstrapResult.Created, Selection, List<AiStudioScreenState.File>,
    ) -> Unit,
) {
    val bootstrap = screen.value.sessionBootstrap ?: return
    val selection = bootstrap.picker.confirmed ?: return
    if (bootstrap.project != action.project || bootstrap.picker.step != null || picker == null) return
    if (bootstrap.operation != null || bootstrap.id in screen.value.importingAttachmentsFor) return
    screen.update {
        it.copy(sessionBootstrap = bootstrap.copy(operation = SessionBootstrapState.Operation.CreatingSession))
    }
    when (val result = picker.create(action.prompt, bootstrap.attachments.firstOrNull()?.name)?.getOrNull()) {
        is SessionBootstrapResult.Created -> {
            val confirmed = picker.confirmedConfiguration() ?: selection
            picker.close()
            onCreated(action, result, confirmed, bootstrap.attachments)
        }
        SessionBootstrapResult.EmptyPrompt -> screen.update {
            it.copy(sessionBootstrap = bootstrap.copy(error = SessionBootstrapState.Error.EmptyPrompt))
        }
        SessionBootstrapResult.ProjectUnavailable, null -> {
            val error = if (result == null) SessionBootstrapState.Error.StorageFailure
            else SessionBootstrapState.Error.ProjectUnavailable
            screen.update { it.copy(sessionBootstrap = bootstrap.copy(project = action.project, error = error)) }
        }
    }
}
