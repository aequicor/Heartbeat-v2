package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbDialog
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.cancel_project_registration
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.create_project
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_project_name
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_name_conflict
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_name_invalid
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_registration_failed
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraProjectNameDialog(
    bootstrap: SessionBootstrapState,
    busy: Boolean,
    onAction: (AiStudioAction.BootstrapProject) -> Unit,
) {
    val pending = bootstrap.pendingProject ?: return
    var name by remember(pending.workingDirectoryPath) { mutableStateOf("") }
    var submitted by remember(pending.workingDirectoryPath) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val canSubmit = name.isNotBlank() && !busy
    val cancel = { onAction(AiStudioAction.BootstrapProject.Cancel) }
    val submit = {
        if (canSubmit) {
            submitted = true
            onAction(AiStudioAction.BootstrapProject.Retry(name))
        }
    }
    val fieldLabel = stringResource(Res.string.new_project_name)
    val error = if (!submitted || busy) null else when (bootstrap.error) {
        is SessionBootstrapState.Error.ProjectNameTaken -> stringResource(Res.string.project_name_conflict)
        SessionBootstrapState.Error.InvalidName -> stringResource(Res.string.project_name_invalid)
        null -> null
        else -> stringResource(Res.string.project_registration_failed)
    }

    HbDialog(
        title = stringResource(Res.string.project_name_conflict),
        onDismissRequest = cancel,
        dismissOnClickOutside = !busy,
        dismissOnBackPress = !busy,
        actions = {
            HbButton(
                stringResource(Res.string.cancel_project_registration), cancel,
                enabled = !busy, variant = HbButtonVariant.Ghost,
            )
            HbButton(stringResource(Res.string.create_project), submit, enabled = canSubmit, busy = busy)
        },
    ) {
        HbTextField(
            name,
            { name = it },
            Modifier.width(HbAuroraLayout.ProjectNameFieldWidth).focusRequester(focus)
                .semantics { contentDescription = fieldLabel }
                .projectNameKeys(busy, submit, cancel),
            placeholder = fieldLabel,
            errorMessage = error,
            readOnly = busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (error != null) {
            AuroraText(
                error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = HbTheme.colors.status.error.foreground, small = true,
            )
        }
    }
    LaunchedEffect(focus) {
        withFrameNanos { }
        focus.requestFocus()
    }
}

private fun Modifier.projectNameKeys(busy: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        when (event.key) {
            Key.Enter, Key.NumPadEnter -> {
                if (event.type == KeyEventType.KeyDown) onSubmit()
                true
            }
            Key.Escape -> {
                if (!busy && event.type == KeyEventType.KeyDown) onCancel()
                true
            }
            else -> false
        }
    }
