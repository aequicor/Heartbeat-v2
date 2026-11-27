package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult

@Composable
internal actual fun AuroraAttachmentPreviewWindow(
    file: File,
    previews: AuroraDiagramPreviews,
    onSaveEditedImage: ((ImageEditResult) -> Unit)?,
    copyError: Boolean,
    onClose: () -> Unit,
) {
    AuroraAttachmentLayer(onClose) {
        var actionError by remember(file.openId) { mutableStateOf<String?>(null) }
        val copy = rememberAttachmentCopyLauncher { failed ->
            actionError = if (failed) "Не удалось скопировать вложение" else null
        }
        val save = rememberArtifactSaveLauncher { failed ->
            actionError = if (failed) "Не удалось сохранить вложение" else null
        }
        CompositionLocalProvider(LocalDiagramPreviews provides previews) {
            AuroraDesktopAttachmentPreview(
                file = file,
                onCopy = { copy(file) },
                onSave = { save(file) },
                onClose = onClose,
                actionError = if (copyError) "Не удалось сохранить копию изображения" else actionError,
                onSaveEditedImage = onSaveEditedImage,
            )
        }
    }
}

@Composable
internal actual fun AttachmentOverlayBackHandler(onClose: () -> Unit) = Unit
