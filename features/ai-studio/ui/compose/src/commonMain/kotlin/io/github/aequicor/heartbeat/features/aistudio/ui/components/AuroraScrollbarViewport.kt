package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.roundToInt

internal data class AuroraScrollbarViewport(val total: Int, val start: Double, val end: Double) {
    val visibleSpan get() = end - start
    val fraction get() = (visibleSpan / total).toFloat()
    val progress: Float get() {
        val travel = start + (total - end)
        return if (travel > 0.0) (start / travel).toFloat() else 0f
    }
}

internal fun LazyListState.auroraScrollbarViewport(): AuroraScrollbarViewport? {
    val backward = canScrollBackward
    val forward = canScrollForward
    val info = layoutInfo
    val spacing = info.mainAxisItemSpacing
    // The safe track represents the interval between the content insets. Its origin matches scrollToItem.
    val leadingEdge = info.viewportStartOffset + info.beforeContentPadding
    val trailingEdge = info.viewportEndOffset - info.afterContentPadding
    val first = info.visibleItemsInfo.firstOrNull { it.offset + it.extent(spacing) > leadingEdge }
    val last = info.visibleItemsInfo.lastOrNull { it.offset < trailingEdge }
    val hasReadingViewport = (backward || forward) && leadingEdge < trailingEdge
    if (!hasReadingViewport || first == null || last == null) return null
    val start = if (backward) first.fractionalIndex(leadingEdge, spacing) else 0.0
    val end = if (forward) last.fractionalIndex(trailingEdge, spacing) else info.totalItemsCount.toDouble()
    return if (end > start) AuroraScrollbarViewport(info.totalItemsCount, start, end) else null
}

internal fun LazyListState.auroraScrollToProgress(
    progress: Float,
    dragVisibleSpan: Double? = null,
    previousProgress: Float? = null,
) {
    val viewport = auroraScrollbarViewport() ?: return
    // A changing set of visible row heights must not change the mapping underneath a held pointer.
    val span = dragVisibleSpan ?: viewport.visibleSpan
    when (progress) {
        0f -> requestScrollToItem(0)
        // Let LazyList clamp at the end, even when its last item is taller than the viewport.
        1f -> requestScrollToItem(viewport.total - 1, Int.MAX_VALUE / 2)
        else -> requestScrollToFractionalIndex(
            progress * (viewport.total - span).coerceAtLeast(0.0),
            viewport.total,
            towardStart = progress < (previousProgress ?: viewport.progress),
        )
    }
}

private fun LazyListState.requestScrollToFractionalIndex(position: Double, total: Int, towardStart: Boolean) {
    val info = layoutInfo
    val index = position.toInt().coerceIn(0, total - 1)
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    // Enter unmeasured rows from the direction of travel so correcting to the measured offset never doubles back.
    if (item == null && towardStart && index < total - 1) {
        requestScrollToItem(index + 1, -1)
        return
    }
    val extent = item?.extent(info.mainAxisItemSpacing) ?: 0
    val offset = ((position - index) * extent).roundToInt()
    requestScrollToItem(index, offset)
}

private fun LazyListItemInfo.fractionalIndex(edge: Int, spacing: Int) =
    index + ((edge - offset).toDouble() / extent(spacing).toDouble()).coerceIn(0.0, 1.0)

private fun LazyListItemInfo.extent(spacing: Int) = (size + spacing).coerceAtLeast(1)
