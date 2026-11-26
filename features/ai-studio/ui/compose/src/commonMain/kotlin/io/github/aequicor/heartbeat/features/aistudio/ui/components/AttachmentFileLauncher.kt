package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction

@Composable
internal expect fun rememberAttachmentFileLauncher(
    onResult: (reference: String?) -> Unit,
    onError: () -> Unit,
): () -> Unit

@Composable
internal fun AuroraAttachmentPickerButton(
    draftId: String,
    importing: Boolean,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    enabled: Boolean = true,
) {
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val open = rememberAttachmentFileLauncher(
        onResult = { reference ->
            val selectedFor = target
            target = null
            if (selectedFor != null && reference != null) {
                onAction(AiStudioAction.ImportAttachment(selectedFor, reference))
            }
        },
        onError = {
            target?.let { onAction(AiStudioAction.AttachmentPickerFailed(it)) }
            target = null
        },
    )
    AuroraAttachButton(
        touch,
        onClick = {
            target = draftId
            open()
        },
        enabled = enabled && target == null,
        demo = false,
        importing = importing,
    )
}
