package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor as Editor
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor.Mark
import io.github.aequicor.heartbeat.features.aistudio.ui.components.controlWheelZoom
import kotlin.math.roundToInt

@Composable
internal fun ImageEditorCanvas(
    original: ImageBitmap,
    document: ImageEditDocument,
    tool: ImageEditorTool?,
    settings: ImageEditorToolSettings,
    crop: IntRect?,
    viewport: ImageEditorViewport,
    onViewport: (ImageEditorViewport) -> Unit,
    onPan: (Offset) -> Unit,
    onCrop: (IntRect) -> Unit,
    onAnnotation: (ImageEditAnnotation) -> Unit,
    onPoint: (Offset) -> Unit,
    onMark: (Int) -> Unit,
    onZoomAnchor: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(Rect, IntSize) -> Unit = { _, _ -> },
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pending by remember { mutableStateOf<ImageEditAnnotation?>(null) }
    var cropPreview by remember { mutableStateOf<IntRect?>(null) }
    val density = LocalDensity.current.density
    val viewportState by rememberUpdatedState(viewport)
    val settingsState by rememberUpdatedState(settings)
    val cropState by rememberUpdatedState(crop)
    val viewportChange by rememberUpdatedState(onViewport)
    val panChange by rememberUpdatedState(onPan)
    val cropChange by rememberUpdatedState(onCrop)
    val annotationChange by rememberUpdatedState(onAnnotation)
    val pointChange by rememberUpdatedState(onPoint)
    val zoomAnchorChange by rememberUpdatedState(onZoomAnchor)
    val imageBounds = viewport.bounds(canvasSize, document.size, density)
    Box(
        modifier.clipToBounds().onSizeChanged { canvasSize = it }
            .controlWheelZoom { factor, relative -> onViewport(viewport.zoomAround(factor, relative)) }
            .pointerInput(document.size, density) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointer = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Scroll, PointerEventType.PanMove -> {
                                if (pointer.isConsumed || event.keyboardModifiers.isCtrlPressed) continue
                                val delta = if (event.type == PointerEventType.Scroll) {
                                    pointer.scrollDelta * Editor.ScrollPanStep.toPx()
                                } else pointer.panOffset
                                if (delta == Offset.Zero) continue
                                panChange(-delta)
                                pointer.consume()
                            }
                            PointerEventType.Move, PointerEventType.Enter, PointerEventType.Press -> {
                                if (pointer.type != PointerType.Mouse) continue
                                if (viewportState.bounds(size, document.size, density).contains(pointer.position)) {
                                    zoomAnchorChange(pointer.position - Offset(size.width / 2f, size.height / 2f))
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = "Холст редактора" }
                .imageEditorPlatformZoom { factor, anchor ->
                    onViewport(viewport.zoomAt(factor, anchor, canvasSize))
                }
                .pointerInput(tool, document.size, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                        val initialViewport = viewportState
                        val bounds = initialViewport.bounds(size, document.size, density)
                        if (bounds.width <= 0f) return@awaitEachGesture
                        val inside = bounds.contains(down.position)
                        val start = bounds.toImage(down.position, document.size)
                        val initialCrop = cropState
                        val screenCrop = initialCrop?.let { bounds.toCanvas(it, document.size) }
                        val handle = screenCrop?.let { target ->
                            cropHandles.minBy { (target.at(it) - down.position).getDistance() }
                                .takeIf { (target.at(it) - down.position).getDistance() <= Editor.CropHandleHitRadius.toPx() }
                        }
                        val moveCrop = initialCrop != null && screenCrop?.contains(down.position) == true
                        val points = mutableListOf(start)
                        var localViewport = initialViewport
                        var editedCrop: IntRect? = null
                        var moved = false
                        var pinch = false
                        var last = start
                        val activeSettings = settingsState
                        down.consume()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll || event.type == PointerEventType.PanMove) continue
                                val change = event.changes.firstOrNull { it.id == down.id }
                                val multiple = event.changes.count { it.pressed || it.previousPressed } > 1
                                if (multiple) {
                                    pinch = true
                                    pending = null
                                    cropPreview = null
                                    val anchor = event.calculateCentroid(useCurrent = false)
                                    if (anchor != Offset.Unspecified) {
                                        localViewport = localViewport.zoomAt(event.calculateZoom(), anchor, size, event.calculatePan())
                                        viewportChange(localViewport)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                if (change == null || change.isConsumed && !pinch) return@awaitEachGesture
                                if (!change.pressed) break
                                if (pinch || change.position == change.previousPosition) continue
                                moved = moved || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                val currentBounds = viewportState.bounds(size, document.size, density)
                                last = currentBounds.toImage(change.position, document.size)
                                if (!inside && handle == null || tool == null) {
                                    localViewport = localViewport.copy(pan = localViewport.pan + change.position - change.previousPosition)
                                    viewportChange(localViewport)
                                } else when (tool) {
                                    ImageEditorTool.Crop -> {
                                        editedCrop = when {
                                            initialCrop != null && handle != null ->
                                                activeSettings.aspect.resize(initialCrop, handle, last - start, document.size)
                                            initialCrop != null && moveCrop -> initialCrop.moveBy(last - start, document.size)
                                            else -> activeSettings.aspect.fromDrag(start, last, document.size)
                                        }
                                        cropPreview = editedCrop
                                    }
                                    ImageEditorTool.Brush -> {
                                        points += last
                                        pending = activeSettings.brush(points.toList())
                                    }
                                    ImageEditorTool.Shapes -> pending = activeSettings.shape(start, last)
                                    else -> Unit
                                }
                                change.consume()
                            }
                            if (pinch || !inside && handle == null) return@awaitEachGesture
                            when (tool) {
                                ImageEditorTool.Crop -> editedCrop?.let(cropChange)
                                ImageEditorTool.Brush -> annotationChange(activeSettings.brush(points.toList()))
                                ImageEditorTool.Shapes -> if ((last - start).getDistance() >= 1f) {
                                    annotationChange(activeSettings.shape(start, last))
                                }
                                ImageEditorTool.Mark, ImageEditorTool.Text -> if (!moved) pointChange(start)
                                null -> Unit
                            }
                        } finally {
                            pending = null
                            cropPreview = null
                        }
                    }
                },
        ) {
            if (imageBounds.width > 0f) {
                drawImageEdit(original, document, imageBounds.topLeft, imageBounds.width / document.size.width, pending)
                (cropPreview ?: crop)?.let { drawCrop(imageBounds, imageBounds.toCanvas(it, document.size)) }
            }
        }
        if (imageBounds.width > 0f) {
            document.description.marks.forEachIndexed { index, mark ->
                val at = imageBounds.pixelCenter(mark.x, mark.y, document.size)
                ImageMarkBadge(mark, index + 1, at, onClick = { onMark(index) })
            }
            overlay(imageBounds, canvasSize)
        }
    }
}

@Composable
internal fun ImageMarkBadge(mark: Mark, number: Int, at: Offset, onClick: () -> Unit) {
    val radius = with(LocalDensity.current) { (Editor.MarkTarget / 2).roundToPx() }
    Box(
        Modifier.offset { IntOffset(at.x.roundToInt() - radius, at.y.roundToInt() - radius) }
            .size(Editor.MarkTarget)
            .semantics { contentDescription = "Метка $number · ${mark.name}" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(Editor.MarkDiameter).background(Colors.DesktopPrimaryAction, CircleShape)
                .border(Layout.Stroke, Colors.DesktopOnPrimaryAction, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(number.toString(), style = HbTypography.Caption.copy(
                color = Colors.DesktopOnPrimaryAction, fontSize = Editor.MarkNumber, fontWeight = FontWeight.SemiBold,
            ), maxLines = 1)
        }
    }
}

internal fun Rect.pixelCenter(x: Int, y: Int, size: IntSize): Offset =
    Offset(left + (x + 0.5f) * width / size.width, top + (y + 0.5f) * height / size.height)

internal expect fun Modifier.imageEditorPlatformZoom(onZoom: (Float, Offset) -> Unit): Modifier

private fun Rect.toImage(point: Offset, image: IntSize): Offset = Offset(
    ((point.x - left) / width * image.width).coerceIn(0f, image.width.toFloat()),
    ((point.y - top) / height * image.height).coerceIn(0f, image.height.toFloat()),
)

private fun Rect.toCanvas(rect: IntRect, image: IntSize): Rect = Rect(
    left + rect.left * width / image.width, top + rect.top * height / image.height,
    left + rect.right * width / image.width, top + rect.bottom * height / image.height,
)

private fun IntRect.moveBy(delta: Offset, image: IntSize): IntRect {
    val x = (left + delta.x).roundToInt().coerceIn(0, image.width - width)
    val y = (top + delta.y).roundToInt().coerceIn(0, image.height - height)
    return IntRect(x, y, x + width, y + height)
}

private val cropHandles = listOf(
    Offset(0f, 0f), Offset(0.5f, 0f), Offset(1f, 0f), Offset(1f, 0.5f),
    Offset(1f, 1f), Offset(0.5f, 1f), Offset(0f, 1f), Offset(0f, 0.5f),
)

private fun Rect.at(fraction: Offset): Offset = Offset(left + width * fraction.x, top + height * fraction.y)

private fun DrawScope.drawCrop(image: Rect, crop: Rect) {
    clipRect(crop.left, crop.top, crop.right, crop.bottom, clipOp = ClipOp.Difference) {
        drawRect(Editor.CropMask, image.topLeft, image.size)
    }
    val guide = Editor.CropGuide
    for (third in 1..2) {
        val x = crop.left + crop.width * third / 3
        val y = crop.top + crop.height * third / 3
        drawLine(guide, Offset(x, crop.top), Offset(x, crop.bottom), Layout.Stroke.toPx())
        drawLine(guide, Offset(crop.left, y), Offset(crop.right, y), Layout.Stroke.toPx())
    }
    drawRect(Editor.CropOutline, crop.topLeft, crop.size, style = Stroke(Layout.Stroke.toPx()))
    val handle = Editor.CropHandle.toPx()
    for (position in cropHandles) {
        val at = crop.at(position) - Offset(handle / 2, handle / 2)
        drawRect(Editor.CropOutline, at, Size(handle, handle))
        drawRect(Colors.DesktopPrimaryAction, at, Size(handle, handle), style = Stroke(Layout.Stroke.toPx()))
    }
}
