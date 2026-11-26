package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction

@Composable
internal actual fun rememberAttachmentInput(
    draftId: String?,
    importing: Boolean,
    onAction: (AiStudioAction) -> Unit,
): AttachmentInput = AttachmentInput(Modifier, Modifier)
