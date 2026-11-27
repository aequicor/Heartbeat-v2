package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentImportSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

@Composable
internal expect fun attachmentImageEditSource(): (ByteArray) -> AttachmentImportSource

internal fun File.editedImagePreview(bytes: ByteArray): File = copy(
    content = Base64.encode(bytes),
    image = ImageAttachment("image/png"),
    mimeType = "image/png",
    base64Encoded = true,
    sourceSizeBytes = bytes.size.toLong(),
)

@Composable
internal fun AuroraEditImageButton(
    file: File,
    onApply: (ByteArray) -> Unit,
    confirmLabel: String = "Применить",
    trigger: @Composable (onClick: () -> Unit) -> Unit = { onClick ->
        AuroraControl(
            onClick, label = "Редактировать изображение", round = false,
            background = Colors.ViewerToolbar, hoverBackground = Colors.WorkHover,
        ) {
            AuroraText("Редактировать изображение", color = Colors.DesktopPrimaryAction)
        }
    },
) {
    var editing by remember(file.openId) { mutableStateOf(false) }
    trigger { editing = true }
    if (editing) {
        AuroraImageEditDialog(file, confirmLabel, onDismiss = { editing = false }) {
            editing = false
            onApply(it)
        }
    }
}

@Composable
@Suppress("InjectDispatcher") // Bitmap work is scoped to this dialog.
private fun AuroraImageEditDialog(
    file: File,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onApply: (ByteArray) -> Unit,
) {
    val decoded by produceState<Result<ImageBitmap>?>(null, file.openId, file.content) {
        value = withContext(Dispatchers.Default) {
            runCatching { decodeAttachmentPreview(requireNotNull(file.imageBytes()), requireNotNull(file.image)) }
        }
    }
    var edit by remember(file.openId) { mutableStateOf(AttachmentImageEdit()) }
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val image = decoded?.getOrNull()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(Layout.ContentInset).widthIn(max = 680.dp).fillMaxWidth()
                .background(Colors.ViewerSurface, RoundedCornerShape(Layout.ComposerRadius)).padding(Layout.PaneGap),
            verticalArrangement = Arrangement.spacedBy(Layout.Space),
        ) {
            AuroraText("Редактирование изображения", color = Colors.DesktopInk)
            AuroraText("Выделите область, которую нужно оставить", small = true, color = Colors.DesktopWorkMuted)
            Box(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 160.dp, max = 420.dp)
                    .background(Colors.ViewerToolbar),
                contentAlignment = Alignment.Center,
            ) {
                if (image == null) {
                    AuroraText(if (decoded?.isFailure == true) "Не удалось открыть изображение" else "Загрузка изображения…")
                } else AttachmentCropCanvas(image, edit, enabled = !exporting) { edit = edit.copy(crop = it) }
            }
            if (exportFailed) AuroraText("Не удалось подготовить изображение", color = Colors.Error)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Layout.ControlGap),
                verticalArrangement = Arrangement.spacedBy(Layout.ControlGap),
            ) {
                ImageEditButton("Повернуть на 90°", image != null && !exporting) { edit = edit.rotateRight() }
                ImageEditButton("Сбросить", edit != AttachmentImageEdit() && !exporting) { edit = AttachmentImageEdit() }
                ImageEditButton("Отмена", true, onDismiss)
                AuroraControl(
                    {
                        val original = image ?: return@AuroraControl
                        val selected = edit
                        exporting = true
                        exportFailed = false
                        scope.launch {
                            val png = withContext(Dispatchers.Default) {
                                runCatching { selected.render(original).encodeAttachmentPng() }.getOrNull()
                            }
                            exporting = false
                            if (png == null) exportFailed = true else onApply(png)
                        }
                    },
                    label = confirmLabel, round = false, background = Colors.DesktopPrimaryAction,
                    enabled = image != null && edit != AttachmentImageEdit() && !exporting,
                ) {
                    AuroraText(confirmLabel, color = Colors.DesktopOnPrimaryAction)
                }
            }
        }
    }
}

@Composable
private fun ImageEditButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    AuroraControl(
        onClick, label = label, enabled = enabled, round = false,
        background = Colors.ViewerToolbar, hoverBackground = Colors.WorkHover,
    ) {
        AuroraText(label, color = if (enabled) Colors.DesktopInk else Colors.DesktopWorkMuted)
    }
}

@Composable
private fun AttachmentCropCanvas(
    image: ImageBitmap,
    edit: AttachmentImageEdit,
    enabled: Boolean,
    onCrop: (Rect) -> Unit,
) {
    val rotated = edit.rotatedSize(image)
    val paint = remember { Paint() }
    Canvas(
        Modifier.fillMaxSize().semantics { contentDescription = "Область кадрирования" }
            .pointerInput(rotated, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val bounds = imageBounds(size.width.toFloat(), size.height.toFloat(), rotated)
                    if (!bounds.contains(down.position)) return@awaitEachGesture
                    val start = bounds.normalizedPosition(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        val end = bounds.normalizedPosition(change.position)
                        val crop = Rect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
                        if (crop.width * rotated.width >= 1f && crop.height * rotated.height >= 1f) onCrop(crop)
                        change.consume()
                    }
                }
            },
    ) {
        val bounds = imageBounds(size.width, size.height, rotated)
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate(bounds.left, bounds.top)
            canvas.scale(bounds.width / rotated.width, bounds.height / rotated.height)
            edit.draw(canvas, image, paint)
            canvas.restore()
        }
        if (edit.crop != AttachmentImageEdit().crop) {
            val crop = Rect(
                bounds.left + edit.crop.left * bounds.width, bounds.top + edit.crop.top * bounds.height,
                bounds.left + edit.crop.right * bounds.width, bounds.top + edit.crop.bottom * bounds.height,
            )
            clipRect(crop.left, crop.top, crop.right, crop.bottom, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.55f), bounds.topLeft, bounds.size)
            }
            drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(Layout.Stroke.toPx()))
        }
    }
}

private fun imageBounds(width: Float, height: Float, image: IntSize): Rect {
    val scale = minOf(width / image.width, height / image.height)
    val imageSize = Size(image.width * scale, image.height * scale)
    return Rect(Offset((width - imageSize.width) / 2f, (height - imageSize.height) / 2f), imageSize)
}

private fun Rect.normalizedPosition(position: Offset): Offset =
    Offset(((position.x - left) / width).coerceIn(0f, 1f), ((position.y - top) / height).coerceIn(0f, 1f))
