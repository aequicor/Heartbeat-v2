package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentImportSource
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun rememberAttachmentInput(
    draftId: String?,
    importing: Boolean,
    onAction: (AiStudioAction) -> Unit,
): AttachmentInput {
    if (draftId == null) return AttachmentInput(Modifier, Modifier)

    val currentOnAction = rememberUpdatedState(onAction)
    val isImporting = rememberUpdatedState(importing)
    val dropTarget = remember(draftId) {
        object : DragAndDropTarget {

            override fun onDrop(event: DragAndDropEvent): Boolean {
                if (isImporting.value) return false
                val references = try {
                    event.awtTransferable.fileReferences()
                } catch (_: Exception) {
                    currentOnAction.value(AiStudioAction.AttachmentPickerFailed(draftId))
                    return false
                }
                if (references.isEmpty()) return false
                currentOnAction.value(AiStudioAction.ImportAttachments(draftId, { it(references) }))
                // A successful MOVE authorizes the source to remove the files.
                return event.action != DragAndDropTransferAction.Move
            }
        }
    }
    return AttachmentInput(
        dropModifier = Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { !importing && it.awtTransferable.hasFiles() },
            target = dropTarget,
        ),
        pasteModifier = Modifier.onPreviewKeyEvent { event ->
            if (!event.isPasteShortcut()) return@onPreviewKeyEvent false
            val source = try {
                Toolkit.getDefaultToolkit().systemClipboard.getContents(null)?.clipboardAttachment(draftId)
            } catch (_: Exception) {
                currentOnAction.value(AiStudioAction.AttachmentPickerFailed(draftId))
                return@onPreviewKeyEvent false
            } ?: return@onPreviewKeyEvent false
            if (!isImporting.value) {
                currentOnAction.value(source)
            }
            true
        },
    )
}

private val macOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

private fun KeyEvent.isPasteShortcut(): Boolean =
    type == KeyEventType.KeyDown && key == Key.V && !isShiftPressed && !isAltPressed &&
        if (macOs) isMetaPressed && !isCtrlPressed else isCtrlPressed && !isMetaPressed

private fun Transferable.hasFiles(): Boolean =
    isDataFlavorSupported(DataFlavor.javaFileListFlavor) || fileUriFlavor() != null

private fun Transferable.fileUriFlavor(): DataFlavor? =
    transferDataFlavors.firstOrNull {
        it.isMimeTypeEqual("text/uri-list") || it.isMimeTypeEqual("x-special/gnome-copied-files")
    }

private fun Transferable.fileReferences(): List<String> {
    if (isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val files = getTransferData(DataFlavor.javaFileListFlavor) as List<*>
        val references = files.filterIsInstance<File>().map(File::getAbsolutePath)
        if (references.isNotEmpty()) return references
    }
    val flavor = fileUriFlavor() ?: return emptyList()
    return flavor.getReaderForText(this).buffered().useLines { lines ->
        lines.mapNotNull { line ->
            try {
                val uri = URI(line)
                if (uri.scheme.equals("file", ignoreCase = true)) Path.of(uri).toString() else null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: java.net.URISyntaxException) {
                null
            }
        }.toList()
    }
}

private fun Transferable.clipboardAttachment(draftId: String): AiStudioAction.ImportAttachments? {
    val references = fileReferences()
    if (references.isNotEmpty()) {
        return AiStudioAction.ImportAttachments(draftId, { it(references) }, AiStudioAction.ImportAttachments.Origin.ClipboardFile)
    }
    if (!isDataFlavorSupported(DataFlavor.imageFlavor)) return null
    val source = AttachmentImportSource { importFiles ->
        withContext(Dispatchers.IO) {
            val image = getTransferData(DataFlavor.imageFlavor) as Image
            val directory = Files.createTempDirectory("heartbeat-clipboard-")
            val file = directory.resolve("clipboard.png")
            try {
                check(ImageIO.write(image.toBufferedImage(), "png", file.toFile()))
                importFiles(listOf(file.toString()))
            } finally {
                try {
                    Files.deleteIfExists(file)
                } finally {
                    Files.deleteIfExists(directory)
                }
            }
        }
    }
    return AiStudioAction.ImportAttachments(draftId, source, AiStudioAction.ImportAttachments.Origin.ClipboardImage)
}

private fun Image.toBufferedImage(): BufferedImage {
    if (this is BufferedImage) return this
    val image = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        check(graphics.drawImage(this, 0, 0, null))
    } finally {
        graphics.dispose()
    }
    return image
}
