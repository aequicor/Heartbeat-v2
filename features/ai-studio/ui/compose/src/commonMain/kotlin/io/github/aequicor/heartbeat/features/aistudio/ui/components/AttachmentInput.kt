package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction

internal data class AttachmentInput(val dropModifier: Modifier, val pasteModifier: Modifier)

@Composable
internal expect fun rememberAttachmentInput(
    draftId: String?,
    importing: Boolean,
    onAction: (AiStudioAction) -> Unit,
): AttachmentInput
