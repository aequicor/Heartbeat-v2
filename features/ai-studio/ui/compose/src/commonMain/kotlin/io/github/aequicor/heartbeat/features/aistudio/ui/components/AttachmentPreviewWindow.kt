package io.github.aequicor.heartbeat.features.aistudio.ui.components

import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult
import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

@Composable
internal expect fun AuroraAttachmentPreviewWindow(
    file: File,
    previews: AuroraDiagramPreviews,
    onSaveEditedImage: ((ImageEditResult) -> Unit)? = null,
    copyError: Boolean = false,
    onClose: () -> Unit,
)
