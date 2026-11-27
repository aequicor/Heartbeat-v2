package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor as Editor
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor.Mark
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentImportSource
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditAnnotation
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditDocument
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditHistory
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorCanvas
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorFooter
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorHeader
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorMarkForm
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorSaveForm
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorTextForm
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorTool
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorToolSettings
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorViewport
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.imageEditorDialogProperties
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.pixelCenter
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorPngQuality
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorPreparedCopy
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.encodeCompactAttachmentPng
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.withLongSide
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.resizeMarks
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.render
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorPngQuality.Original
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

@Composable
internal expect fun attachmentImageEditSource(): (ByteArray) -> AttachmentImportSource

@Composable
internal fun AuroraEditableImagePreview(
    file: File,
    onApply: (ImageEditResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember(file.openId) { mutableStateOf(false) }
    val open = { editing = true }
    val label = "Редактировать изображение"
    Box(modifier.semantics { contentDescription = label }.clickable(role = Role.Button, onClick = open)) {
        AuroraImagePreview(file, Modifier.fillMaxSize(), onMarkClick = open)
        val shape = RoundedCornerShape(Layout.SmallRadius)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(Layout.Space).size(Editor.ToolSize)
                .background(Colors.ViewerSurface, shape).border(Layout.Stroke, Colors.Line, shape),
            contentAlignment = Alignment.Center,
        ) {
            HbIcon(HbIconGlyph.Pencil, tint = Colors.DesktopInk, size = Layout.Glyph)
        }
    }
    if (editing) {
        val close = { editing = false }
        val shape = RoundedCornerShape(Layout.ChatSurfaceRadius)
        Dialog(onDismissRequest = close, properties = imageEditorDialogProperties()) {
            AuroraAttachmentImageEditor(
                file = file,
                onClose = close,
                onApply = {
                    close()
                    onApply(it)
                },
                modifier = Modifier.padding(Layout.ContentInset)
                    .sizeIn(maxWidth = Editor.DialogWidth, maxHeight = Editor.DialogHeight)
                    .clip(shape).border(Layout.Stroke, Colors.Line, shape),
                draft = true,
            )
        }
    }
}

@Composable
@Suppress("InjectDispatcher") // Decode only for the lifetime of the editor.
internal fun AuroraAttachmentImageEditor(
    file: File,
    onClose: () -> Unit,
    onApply: ((ImageEditResult) -> Unit)?,
    modifier: Modifier = Modifier,
    draft: Boolean = false,
    actionError: String? = null,
    actions: @Composable () -> Unit = {},
) = key(file.openId) {
    val decoded by produceState<Result<ImageBitmap>?>(null, file.content) {
        value = withContext(Dispatchers.Default) {
            runCatching { decodeAttachmentPreview(requireNotNull(file.imageBytes()), requireNotNull(file.image)) }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val surface = modifier.fillMaxSize().background(Colors.ViewerSurface)
    val image = decoded?.getOrNull()
    if (image != null) {
        AuroraImageEditor(file, image, draft, onClose, onApply, actionError, actions, focus, surface)
    } else Column(surface.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            onClose()
            true
        } else false
    }.focusRequester(focus).focusable()) {
        ImageEditorHeader(file, null, null, false, {}, {}, {}, onClose, editable = false, actions = actions)
        Box(Modifier.fillMaxSize().background(Editor.Canvas), contentAlignment = Alignment.Center) {
            AuroraText(if (decoded == null) "Загрузка изображения…" else "Не удалось открыть изображение")
        }
    }
}

@Composable
private fun AuroraImageEditor(
    file: File,
    original: ImageBitmap,
    draft: Boolean,
    onClose: () -> Unit,
    onSave: ((ImageEditResult) -> Unit)?,
    actionError: String?,
    actions: @Composable () -> Unit,
    focus: FocusRequester,
    modifier: Modifier,
) {
    val initial = remember(original, file.description) {
        ImageEditDocument(IntSize(original.width, original.height), ImageEditor.fromDescription(file.description))
    }
    var history by remember(initial) { mutableStateOf(ImageEditHistory(initial)) }
    var viewport by remember(initial) { mutableStateOf(ImageEditorViewport()) }
    var zoomAnchor by remember(initial) { mutableStateOf(Offset.Zero) }
    var tool by remember { mutableStateOf<ImageEditorTool?>(null) }
    var settings by remember { mutableStateOf(ImageEditorToolSettings()) }
    var crop by remember { mutableStateOf<IntRect?>(null) }
    var textPoint by remember { mutableStateOf<Offset?>(null) }
    var newMark by remember { mutableStateOf<Mark?>(null) }
    var markIndex by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val document = history.current
    val mark = newMark ?: markIndex?.let(document.description.marks::getOrNull)
    val textMeasurer = rememberTextMeasurer()

    fun closeMark() {
        newMark = null
        markIndex = null
    }

    fun resetTool() {
        tool = null
        crop = null
        closeMark()
        textPoint = null
    }

    fun updateMarks(marks: List<Mark>) {
        history = history.commit(document.copy(description = document.description.copy(marks = marks)))
        closeMark()
    }

    var editorOrigin by remember { mutableStateOf(Offset.Zero) }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    Box(modifier.onGloballyPositioned { editorOrigin = it.positionInRoot() }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) return@onKeyEvent false
        if (mark != null) closeMark() else onClose()
        true
    }) {
        Column(Modifier.fillMaxSize().focusRequester(focus).focusable()) {
            ImageEditorHeader(
                file, document.size, tool, onSave != null && crop == null,
                onTool = { chosen ->
                    closeMark()
                    textPoint = null
                    tool = chosen.takeUnless { it == tool }
                    crop = if (tool == ImageEditorTool.Crop) settings.aspect.centered(document.size) else null
                },
                onRotate = {
                    history = history.commit(document.rotateRight())
                    resetTool()
                },
                onSave = {
                    closeMark()
                    textPoint = null
                    saving = true
                },
                onClose = onClose,
                editable = onSave != null,
                actions = actions,
            )
            EditorDivider()
            actionError?.let {
                AuroraText(it, Modifier.fillMaxWidth().padding(Layout.ContentInset), color = Colors.Error)
            }
            if (!draft) AuroraImageDescription(file)
            Box(Modifier.weight(1f).fillMaxWidth().background(Editor.Canvas).padding(Editor.CanvasInset)) {
                ImageEditorCanvas(
                    original, document, tool, settings, crop, viewport,
                    onViewport = { viewport = it },
                    onPan = { viewport = viewport.copy(pan = viewport.pan + it) },
                    onCrop = { crop = it },
                    onAnnotation = { history = history.commit(document.annotate(it)) },
                    onPoint = { point ->
                        when (tool) {
                            ImageEditorTool.Mark -> {
                                newMark = Mark(
                                    floor(point.x).toInt().coerceIn(0, document.size.width - 1),
                                    floor(point.y).toInt().coerceIn(0, document.size.height - 1),
                                    "",
                                )
                                markIndex = null
                            }
                            ImageEditorTool.Text -> textPoint = point
                            else -> Unit
                        }
                    },
                    onMark = { index ->
                        newMark = null
                        markIndex = index
                    },
                    onZoomAnchor = { zoomAnchor = it },
                    modifier = Modifier.fillMaxSize().onGloballyPositioned {
                        canvasOrigin = it.positionInRoot()
                        canvasSize = it.size
                    },
                ) { _, _ ->
                    val at = textPoint
                    if (at != null) {
                        val fontFamily = settings.font.fontFamily()
                        ImageEditorTextForm(onCancel = { textPoint = null }) { text ->
                            val layout = textMeasurer.measure(
                                text, style = TextStyle(color = settings.textColor.color, fontSize = settings.textSize.sp, fontFamily = fontFamily),
                                density = Density(1f),
                            )
                            history = history.commit(document.annotate(ImageEditAnnotation.Label(at, layout)))
                            textPoint = null
                        }
                    }
                }
            }
            EditorDivider()
            ImageEditorFooter(
                (viewport.zoom * 100).roundToInt(), history, tool, settings, crop != null,
                onSettings = {
                    if (it.aspect != settings.aspect && tool == ImageEditorTool.Crop) crop = it.aspect.centered(document.size)
                    settings = it
                },
                onZoom = { factor -> viewport = viewport.zoomAround(factor, zoomAnchor) },
                onCenter = { viewport = ImageEditorViewport() },
                onUndo = {
                    history = history.undo()
                    resetTool()
                },
                onRedo = {
                    history = history.redo()
                    resetTool()
                },
                onApplyCrop = {
                    crop?.let { history = history.commit(document.crop(it)) }
                    resetTool()
                },
            )
        }
        if (mark != null && canvasSize != IntSize.Zero) {
            val bounds = viewport.bounds(canvasSize, document.size, density)
            ImageEditorMarkForm(
                canvasOrigin - editorOrigin + bounds.pixelCenter(mark.x, mark.y, document.size), mark, markIndex != null,
                onSave = { edited ->
                    val index = markIndex
                    updateMarks(if (index == null) document.description.marks + edited
                        else document.description.marks.mapIndexed { i, current -> if (i == index) edited else current })
                },
                onDelete = { updateMarks(document.description.marks.filterIndexed { index, _ -> index != markIndex }) },
                onCancel = ::closeMark,
                readOnly = onSave == null,
            )
        }
    }
    if (saving && onSave != null) {
        ImageEditorExport(original, document, file, draft, history.changed, { saving = false }, {
            saving = false
            onSave(it)
        }) { description ->
            history = history.commit(document.copy(description = description))
        }
    }
}

@Composable
@Suppress("InjectDispatcher") // The full-size render and thumbnail are prepared for this preview.
private fun ImageEditorExport(
    original: ImageBitmap,
    document: ImageEditDocument,
    file: File,
    draft: Boolean,
    edited: Boolean,
    onCancel: () -> Unit,
    onSave: (ImageEditResult) -> Unit,
    onMarks: (ImageEditor) -> Unit,
) {
    val sourceSide = maxOf(document.size.width, document.size.height)
    var sideInput by remember { mutableStateOf(sourceSide.toString()) }
    var quality by remember { mutableStateOf(Original) }
    val encoder = remember { Mutex() }
    val target = sideInput.toIntOrNull()?.takeIf { it > 0 }?.let(document.size::withLongSide)
        ?.takeIf { file.image?.acceptsDimensions(it.width, it.height) == true }
    val pixelDocument = remember(document.fromOriginal, document.size, document.layers) { document }
    val export = key(pixelDocument, target, quality) {
        val prepared by produceState<Result<ImageEditorPreparedCopy>?>(null) {
            if (target != null) {
                delay(150) // Avoid encoding intermediate values while a custom size is being entered.
                value = withContext(Dispatchers.Default) {
                    encoder.withLock {
                        try {
                            val pixels = pixelDocument.render(original, target)
                            val png = if (quality == Original) {
                                pixels.encodeAttachmentPng()
                            } else {
                                pixels.encodeCompactAttachmentPng(quality)
                            }
                            val preview = if (quality == Original) pixels else png.decodeToImageBitmap()
                            val thumbnailSize = target.withLongSide(minOf(96, maxOf(target.width, target.height)))
                            val thumbnail = ImageEditDocument(target, ImageEditor("", emptyList()))
                                .render(preview, thumbnailSize).encodeAttachmentPng()
                            Result.success(ImageEditorPreparedCopy(png, Base64.encode(thumbnail), preview))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Result.failure(error)
                        }
                    }
                }
            }
        }
        prepared
    }
    val suffix = if (document.layers.isEmpty() && document.description.marks.isEmpty()) "кадр" else "разметка"
    val suggested = "${file.name.substringBeforeLast('.')} — $suffix"
    val rendered = export?.getOrNull()
    val outputSize = target ?: document.size
    val outputDescription = document.description.resizeMarks(document.size, outputSize)
    ImageEditorSaveForm(
        original, document, suggested,
        status = when {
            target == null -> "Недопустимый размер изображения"
            export?.isFailure == true -> "Не удалось подготовить изображение"
            else -> null
        },
        exporting = target != null && export == null,
        saveAvailable = rendered != null && target != null &&
            (edited || target != document.size || quality != Original),
        largeDraft = draft && (rendered?.png?.size ?: 0) > 1024 * 1024,
        longSide = sideInput,
        onLongSideChange = { sideInput = it },
        quality = quality,
        onQualityChange = { quality = it },
        outputSize = outputSize,
        previewImage = rendered?.preview,
        outputBytes = rendered?.png?.size,
        outputDescription = outputDescription,
        onMarks = onMarks,
        onSave = { name, description ->
            if (rendered != null && target != null) {
                val stem = if (name.endsWith(".png", ignoreCase = true)) name.dropLast(4) else name
                onSave(ImageEditResult(
                    rendered.png, rendered.thumbnail, "$stem.png", target, outputDescription.copy(text = description),
                ))
            }
        },
        onCancel = onCancel,
    )
}

@Composable
private fun EditorDivider() {
    Box(Modifier.fillMaxWidth().height(Layout.Stroke).background(Colors.Line))
}
