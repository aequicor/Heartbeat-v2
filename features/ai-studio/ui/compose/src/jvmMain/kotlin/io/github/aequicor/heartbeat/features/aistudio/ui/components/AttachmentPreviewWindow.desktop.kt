package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult

internal actual val attachmentViewerUsesWorkspaceOverlay: Boolean = false

@Composable
internal actual fun AuroraAttachmentPreviewWindow(
    file: File,
    previews: AuroraDiagramPreviews,
    onSaveEditedImage: ((ImageEditResult) -> Unit)?,
    copyError: Boolean,
    onClose: () -> Unit,
) {
    Window(
        onCloseRequest = onClose,
        title = "Heartbeat",
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(1426.dp, 893.dp)),
    ) {
        var actionError by remember(file.openId) { mutableStateOf<String?>(null) }
        val copy = rememberAttachmentCopyLauncher { failed ->
            actionError = if (failed) "Не удалось скопировать вложение" else null
        }
        val save = rememberArtifactSaveLauncher { failed ->
            actionError = if (failed) "Не удалось сохранить вложение" else null
        }
        CompositionLocalProvider(LocalDiagramPreviews provides previews) {
            HbDesktopFocusIndication {
                AuroraDesktopAttachmentPreview(
                    file = file,
                    onCopy = { copy(file) },
                    onSave = { save(file) },
                    onClose = onClose,
                    actionError = if (copyError) "Не удалось сохранить копию изображения" else actionError,
                    onSaveEditedImage = onSaveEditedImage,
                    gestureTarget = window.rootPane,
                )
            }
        }
    }
}
