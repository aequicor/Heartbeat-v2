package io.github.aequicor.heartbeat.features.aistudio.ui.components

import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult
import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

internal actual val attachmentViewerUsesWorkspaceOverlay: Boolean = true

@Composable
internal actual fun AuroraAttachmentPreviewWindow(
    file: File,
    previews: AuroraDiagramPreviews,
    onSaveEditedImage: ((ImageEditResult) -> Unit)?,
    copyError: Boolean,
    onClose: () -> Unit,
) {
    AuroraViewer(file, touch = true, previews = previews, onSaveEditedImage = onSaveEditedImage, copyError = copyError, onClose = onClose)
}
