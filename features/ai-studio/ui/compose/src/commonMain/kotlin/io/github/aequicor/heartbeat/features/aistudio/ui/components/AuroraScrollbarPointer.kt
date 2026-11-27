package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

internal data class AuroraScrollbarPointer(val scrollbar: AuroraScrollbar) :
    ModifierNodeElement<AuroraScrollbarNode>() {
    override fun create() = AuroraScrollbarNode(scrollbar)
    override fun update(node: AuroraScrollbarNode) = node.update(scrollbar)
    override fun InspectorInfo.inspectableProperties() {
        name = "auroraScrollbarPointer"
    }
}

internal class AuroraScrollbarNode(private var scrollbar: AuroraScrollbar) : Modifier.Node(), PointerInputModifierNode {
    override fun sharePointerInputWithSiblings() = true

    fun update(value: AuroraScrollbar) {
        scrollbar.cancel()
        scrollbar = value
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        val size = Size(bounds.width.toFloat(), bounds.height.toFloat())
        for (change in pointerEvent.changes) {
            if (change.type != PointerType.Mouse) continue
            when (pointerEvent.type) {
                PointerEventType.Scroll -> scrollbar.onWheel(change.position)
                PointerEventType.Exit -> scrollbar.onExit()
                PointerEventType.Enter, PointerEventType.Move -> scrollbar.onHover(change.position)
            }
            if (change.changedToDownIgnoreConsumed() && pointerEvent.buttons.isPrimaryPressed && !change.isConsumed) {
                scrollbar.startDrag(change, size)
            } else {
                scrollbar.updateDrag(change, size.height)
            }
        }
    }

    override fun onCancelPointerInput() = scrollbar.cancel()
    override fun onDetach() = scrollbar.cancel()
}

internal class AuroraScrollbar(
    private val list: LazyListState,
    private val reverse: Boolean,
    private val minimum: Float,
    private val onUserScroll: () -> Unit = {},
    private val normalizeThumbLength: Boolean = false,
) {
    private var hover by mutableStateOf<Offset?>(null)
    private var wheelPosition: Offset? = null
    private var initialVisibleSpan: Double? = null
    private var drag by mutableStateOf<Drag?>(null)
    val isDragging get() = drag != null

    fun thumb(height: Float): AuroraScrollbarThumb? = if (height <= 0f) null else drag?.let {
        val length = it.length.coerceAtMost(height)
        val position = list.auroraScrollbarViewport()?.progress?.oriented(reverse) ?: it.fromTop
        AuroraScrollbarThumb(position * (height - length), length)
    } ?: list.auroraScrollbarViewport()?.thumbOnTrack(height)

    private fun AuroraScrollbarViewport.thumbOnTrack(height: Float): AuroraScrollbarThumb {
        // Scrolling changes which rows are measured. Keep their first span for sizing and
        // divide by the current item count so appending a page can still shorten the thumb.
        val span = if (normalizeThumbLength) {
            initialVisibleSpan?.takeIf { it < total } ?: visibleSpan.also { initialVisibleSpan = it }
        } else visibleSpan
        val length = (height * (span / total).toFloat()).coerceIn(minimum.coerceAtMost(height), height)
        return AuroraScrollbarThumb(progress.oriented(reverse) * (height - length), length)
    }

    fun isHovered(thumb: AuroraScrollbarThumb, size: Size) = hover?.let { thumb.contains(it, size) } == true

    fun onHover(position: Offset) {
        if (position == wheelPosition) return
        wheelPosition = null
        hover = position
    }

    fun onWheel(position: Offset) {
        wheelPosition = position
        hover = null
    }

    fun onExit() {
        hover = null
        wheelPosition = null
    }

    fun startDrag(change: PointerInputChange, size: Size) {
        val viewport = list.auroraScrollbarViewport() ?: return
        val thumb = viewport.thumbOnTrack(size.height)
        val travel = size.height - thumb.length
        if (drag != null || travel <= 0f || !thumb.contains(change.position, size)) return
        drag = Drag(change.id, thumb.length, thumb.top / travel, viewport.visibleSpan, edgeFromTop)
        change.consume()
    }

    fun updateDrag(change: PointerInputChange, height: Float) {
        val current = drag ?: return
        if (change.id != current.pointer) return
        if (!change.pressed) {
            drag = null
            change.consume()
        } else if (change.position.y != change.previousPosition.y) {
            moveDrag(current, change.position.y - change.previousPosition.y, height - current.length)
            change.consume()
        }
    }

    private fun moveDrag(current: Drag, delta: Float, travel: Float) {
        if (travel <= 0f) return
        val edge = edgeFromTop
        val enteredEdge = edge?.takeUnless { it == current.observedEdge }
        val previous = enteredEdge ?: current.fromTop
        val next = (previous + delta / travel).coerceIn(0f, 1f)
        if (next == previous) return
        onUserScroll()
        // At an actual edge, use its measured span so reversing takes effect on the next pointer movement.
        val measuredSpan = if (enteredEdge == null) null else list.auroraScrollbarViewport()?.visibleSpan
        val span = measuredSpan ?: current.visibleSpan
        drag = current.copy(fromTop = next, visibleSpan = span, observedEdge = edge)
        list.auroraScrollToProgress(next.oriented(reverse), span, previous.oriented(reverse))
    }

    private val edgeFromTop: Float?
        get() = when {
            !list.canScrollBackward -> 0f.oriented(reverse)
            !list.canScrollForward -> 1f.oriented(reverse)
            else -> null
        }

    fun onKey(key: Key): Boolean = list.auroraScrollbarViewport()?.let { viewport ->
        val fromTop = viewport.progress.oriented(reverse)
        val item = (1.0 / (viewport.total - viewport.visibleSpan)).toFloat()
        val page = item * viewport.visibleSpan.toFloat()
        val target = when (key) {
            Key.MoveHome -> 0f
            Key.MoveEnd -> 1f
            Key.PageUp -> fromTop - page
            Key.PageDown -> fromTop + page
            Key.DirectionUp -> fromTop - item
            Key.DirectionDown -> fromTop + item
            else -> return false
        }.coerceIn(0f, 1f)
        if (target != fromTop) {
            onUserScroll()
            list.auroraScrollToProgress(target.oriented(reverse))
        }
        true
    } ?: false

    fun cancel() {
        onExit()
        drag = null
    }

    private data class Drag(
        val pointer: PointerId,
        val length: Float,
        val fromTop: Float,
        val visibleSpan: Double,
        val observedEdge: Float?,
    )
}

private fun Float.oriented(reverse: Boolean) = if (reverse) 1f - this else this

internal data class AuroraScrollbarThumb(val top: Float, val length: Float) {
    fun contains(position: Offset, size: Size) =
        position.x >= 0f && position.x < size.width && position.y >= top && position.y < top + length
}
