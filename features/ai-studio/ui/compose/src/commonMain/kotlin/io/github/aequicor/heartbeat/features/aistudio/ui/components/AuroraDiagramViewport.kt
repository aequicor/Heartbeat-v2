@file:Suppress("MagicNumber", "DEPRECATION")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import kotlin.math.pow

@Composable
internal fun AuroraDiagramViewport(
    bitmap: ImageBitmap, label: String, width: Dp, height: Dp, touch: Boolean,
    pan: () -> Offset, onPan: (Offset) -> Unit, onScale: (Float) -> Unit,
    onZoomAt: (Float, Offset) -> Unit,
) {
    val transform = rememberTransformableState { factor, delta, _ -> onScale(factor); onPan(delta) }
    val drag = rememberDraggable2DState(onPan)
    val horizontal = rememberScrollableState { delta -> onPan(Offset(delta, 0f)); delta }
    val vertical = rememberScrollableState { delta -> onPan(Offset(0f, delta)); delta }
    val gestures = if (touch) Modifier.transformable(transform) else Modifier
        .draggable2D(drag)
        .scrollable(horizontal, Orientation.Horizontal)
        .scrollable(vertical, Orientation.Vertical)
        .pointerHoverIcon(PointerIcon.Hand)
    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds().then(DiagramWheelInput(onZoomAt)).then(gestures),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val content = with(density) { Size(width.toPx(), height.toPx()) }
        Image(bitmap, label, Modifier.requiredSize(width, height).graphicsLayer {
            translationX = pan().x
            translationY = pan().y
        }, contentScale = ContentScale.FillBounds)
        DiagramScrollbar(
            viewport.width, content.width, { pan().x }, { onPan(Offset(it, 0f)) }, touch,
            horizontal = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(end = Layout.NavigationTrack)
                .fillMaxWidth().height(Layout.NavigationTrack),
        )
        DiagramScrollbar(
            viewport.height, content.height, { pan().y }, { onPan(Offset(0f, it)) }, touch,
            horizontal = false,
            modifier = Modifier.align(Alignment.TopEnd).padding(bottom = Layout.NavigationTrack)
                .fillMaxHeight().width(Layout.NavigationTrack),
        )
    }
}

private data class DiagramRange(val viewport: Float, val maximum: Float, val position: Float) {
    fun thumb(track: Float, minimum: Float) =
        (track * viewport / (viewport + maximum)).coerceIn(minimum.coerceAtMost(track), track)
}

private fun diagramRange(viewport: Float, content: Float, pan: Float): DiagramRange {
    val left = (viewport - content) / 2 + pan
    val start = minOf(0f, left)
    val end = maxOf(viewport, left + content)
    return DiagramRange(viewport, (end - start - viewport).coerceAtLeast(0f), -start)
}

private data class DiagramThumbDrag(val range: DiagramRange, val pan: Float, val scale: Float) {
    fun position(currentPan: Float) = (range.position + pan - currentPan).coerceIn(0f, range.maximum)
}

@Composable
private fun DiagramScrollbar(
    viewport: Float, content: Float, pan: () -> Float, onPan: (Float) -> Unit,
    touch: Boolean, horizontal: Boolean, modifier: Modifier = Modifier,
) {
    val range = { diagramRange(viewport, content, pan()) }
    val overflow by remember(viewport, content, pan) { derivedStateOf { range().maximum > 0f } }
    var drag by remember(viewport, content) { mutableStateOf<DiagramThumbDrag?>(null) }
    if ((!overflow && drag == null) || viewport <= 0f) return
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val minimum = with(LocalDensity.current) { Layout.ScrollbarMinThumb.toPx() }
    var track by remember { mutableIntStateOf(0) }
    val state = rememberDraggableState { delta ->
        drag?.let { gesture ->
            val current = gesture.position(pan())
            val next = (current + delta * gesture.scale).coerceIn(0f, gesture.range.maximum)
            onPan(current - next)
        }
    }
    val input = if (touch) Modifier else Modifier
        .hoverable(interaction).focusable(interactionSource = interaction)
        .diagramScrollbarKeys(horizontal, range, onPan)
        .draggable(
            state, if (horizontal) Orientation.Horizontal else Orientation.Vertical,
            interactionSource = interaction,
            onDragStarted = {
                val current = range()
                val travel = track - current.thumb(track.toFloat(), minimum)
                if (travel > 0f) drag = DiagramThumbDrag(current, pan(), current.maximum / travel)
            },
            onDragStopped = { drag = null },
        )
    Canvas(modifier.onSizeChanged { track = if (horizontal) it.width else it.height }
        .then(input).diagramScrollSemantics(horizontal, range, onPan)) {
        val current = drag?.range ?: range()
        val position = drag?.position(pan()) ?: current.position
        drawDiagramThumb(horizontal, current, position, minimum, hovered || focused, drag != null)
    }
}

private fun DrawScope.drawDiagramThumb(
    horizontal: Boolean, range: DiagramRange, position: Float, minimum: Float, active: Boolean, dragging: Boolean,
) {
    val length = if (horizontal) size.width else size.height
    val thumb = range.thumb(length, minimum)
    val progress = position / range.maximum.coerceAtLeast(1f)
    val thickness = (if (dragging) Layout.Space else if (active) Layout.ControlGap else Layout.SmallGap).toPx()
    val start = (length - thumb) * progress
    drawRoundRect(
        Colors.Ink.copy(alpha = if (dragging) .8f else if (active) .6f else 36f / 255),
        topLeft = if (horizontal) Offset(start, (size.height - thickness) / 2)
            else Offset((size.width - thickness) / 2, start),
        size = if (horizontal) Size(thumb, thickness) else Size(thickness, thumb),
        cornerRadius = CornerRadius(thickness / 2),
    )
}

private fun Modifier.diagramScrollSemantics(
    horizontal: Boolean, range: () -> DiagramRange, onPan: (Float) -> Unit,
) = semantics {
    contentDescription = if (horizontal) "Горизонтальная прокрутка диаграммы" else "Вертикальная прокрутка диаграммы"
    val axis = ScrollAxisRange(value = { range().position }, maxValue = { range().maximum })
    if (horizontal) horizontalScrollAxisRange = axis else verticalScrollAxisRange = axis
    scrollBy { x, y -> scrollDiagram(if (horizontal) x else y, range(), onPan) }
}

private fun scrollDiagram(delta: Float, range: DiagramRange, onPan: (Float) -> Unit): Boolean {
    val next = (range.position + delta).coerceIn(0f, range.maximum)
    onPan(range.position - next)
    return next != range.position
}

private fun Modifier.diagramScrollbarKeys(
    horizontal: Boolean, range: () -> DiagramRange, onPan: (Float) -> Unit,
) = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val current = range()
    val backward = if (horizontal) Key.DirectionLeft else Key.DirectionUp
    val forward = if (horizontal) Key.DirectionRight else Key.DirectionDown
    val delta = when (event.key) {
        Key.MoveHome -> -current.position
        Key.MoveEnd -> current.maximum - current.position
        Key.PageUp -> -current.viewport
        Key.PageDown -> current.viewport
        backward -> -current.viewport / 8
        forward -> current.viewport / 8
        else -> return@onKeyEvent false
    }
    scrollDiagram(delta, current, onPan)
}

private data class DiagramWheelInput(val onZoom: (Float, Offset) -> Unit) : ModifierNodeElement<DiagramWheelNode>() {
    override fun create() = DiagramWheelNode(onZoom)
    override fun update(node: DiagramWheelNode) { node.onZoom = onZoom }
}

private class DiagramWheelNode(var onZoom: (Float, Offset) -> Unit) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial || pointerEvent.type != PointerEventType.Scroll ||
            !pointerEvent.keyboardModifiers.isCtrlPressed) return
        val change = pointerEvent.changes.firstOrNull { !it.isConsumed && it.scrollDelta.y != 0f }
        if (change != null) {
            onZoom(1.25f.pow(-change.scrollDelta.y), change.position - Offset(bounds.width / 2f, bounds.height / 2f))
            pointerEvent.changes.forEach { it.consume() }
        }
    }
    override fun onCancelPointerInput() = Unit
}
