package io.github.aequicor.heartbeat.features.aistudio.ui.components

import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

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

@Composable
internal actual fun AttachmentOverlayBackHandler(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
}
