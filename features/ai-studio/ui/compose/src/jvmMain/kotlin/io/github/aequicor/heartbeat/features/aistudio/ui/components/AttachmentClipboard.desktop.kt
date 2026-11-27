package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.nio.file.Files
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

@Composable
@Suppress("InjectDispatcher")
internal actual fun rememberAttachmentCopyLauncher(onFinished: (failed: Boolean) -> Unit): (File) -> Unit {
    val scope = rememberCoroutineScope()
    val finished = rememberUpdatedState(onFinished)
    return { file ->
        scope.launch {
            val failed = try {
                withContext(Dispatchers.IO) { copyAttachmentToDesktopClipboard(file) }
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
            finished.value(failed)
        }
    }
}

private fun copyAttachmentToDesktopClipboard(file: File) {
    val contents = if (file.image != null) {
        val image = decodeAttachmentPreview(requireNotNull(file.imageBytes()), requireNotNull(file.image))
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        buffered.setRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        AttachmentClipboardValue(DataFlavor.imageFlavor, buffered)
    } else {
        val text = file.previewText()
        if (text != null) StringSelection(text) else {
            val directory = Files.createTempDirectory("heartbeat-copied-attachment")
            directory.toFile().deleteOnExit()
            val name = file.name.replace(UnsafeClipboardFilename, "_").trimEnd('.', ' ')
                .takeUnless { it.isBlank() } ?: "attachment"
            val temporary = directory.resolve(name)
            temporary.toFile().deleteOnExit()
            Files.write(temporary, file.originalBytes())
            AttachmentClipboardValue(DataFlavor.javaFileListFlavor, listOf(temporary.toFile()))
        }
    }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(contents, null)
}

private class AttachmentClipboardValue(
    private val flavor: DataFlavor,
    private val value: Any,
) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(flavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == this.flavor

    override fun getTransferData(flavor: DataFlavor): Any =
        if (isDataFlavorSupported(flavor)) value else throw UnsupportedFlavorException(flavor)
}

private val UnsafeClipboardFilename = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
