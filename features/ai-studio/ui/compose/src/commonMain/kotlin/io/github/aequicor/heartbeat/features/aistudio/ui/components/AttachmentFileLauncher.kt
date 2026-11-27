package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_upload_file
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_attachment_upload
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    AuroraAttachmentUploadMenu(
        draftId = draftId,
        touch = touch,
        enabled = enabled && target == null,
        importing = importing,
    ) {
        target = draftId
        open()
    }
}

@Composable
internal fun AuroraAttachmentUploadMenu(
    draftId: String,
    touch: Boolean,
    enabled: Boolean,
    importing: Boolean,
    onUpload: () -> Unit,
) {
    var expanded by remember(draftId, enabled, importing) { mutableStateOf(false) }
    HbMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        actions = listOf(
            HbMenuAction(
                label = stringResource(Res.string.chat_attachment_upload),
                onClick = onUpload,
                leadingIcon = {
                    Image(
                        painterResource(Res.drawable.attachment_upload_file),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            ),
        ),
        presentation = if (touch) HbMenuPresentation.AuroraTouchMessage else HbMenuPresentation.AuroraAttachment,
    ) { modifier ->
        AuroraAttachButton(
            touch = touch,
            onClick = { expanded = true },
            modifier = modifier,
            enabled = enabled,
            demo = false,
            importing = importing,
        )
    }
}
