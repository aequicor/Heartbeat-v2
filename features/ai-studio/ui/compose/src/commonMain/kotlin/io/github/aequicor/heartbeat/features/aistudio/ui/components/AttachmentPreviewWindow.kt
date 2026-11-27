package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

internal expect val attachmentViewerUsesWorkspaceOverlay: Boolean

@Composable
internal expect fun AuroraAttachmentPreviewWindow(
    file: File,
    previews: AuroraDiagramPreviews,
    onSaveEditedImage: ((ByteArray) -> Unit)? = null,
    copyError: Boolean = false,
    onClose: () -> Unit,
)
