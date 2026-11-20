@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout

@Composable
internal fun AuroraHistoryScrollbar(
    list: LazyListState,
    label: String,
    modifier: Modifier = Modifier,
    reverse: Boolean = true,
    touch: Boolean = false,
) {
    val scrollable by remember(list) { derivedStateOf { list.canScrollBackward || list.canScrollForward } }
    if (!scrollable) return

    val minimum = with(LocalDensity.current) { AuroraLayout.ScrollbarMinThumb.toPx() }
    val scrollbar = remember(list, reverse, minimum) { AuroraScrollbar(list, reverse, minimum) }
    var focused by remember { mutableStateOf(false) }
    val focusVisible = focused && HbFocusIndication.keyboardNavigation
    val keyboard = if (touch) Modifier else Modifier
        .semantics { contentDescription = label }
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { event -> event.type == KeyEventType.KeyDown && scrollbar.onKey(event.key) }
        .focusable()

    Canvas(modifier.fillMaxHeight().width(AuroraLayout.NavigationTrack).then(keyboard)
        .then(AuroraScrollbarPointer(scrollbar))) {
        val thumb = scrollbar.thumb(size.height) ?: return@Canvas
        val active = focusVisible || scrollbar.isHovered(thumb, size)
        drawThumb(thumb, scrollbar.isDragging, active, touch && list.isScrollInProgress)
    }
}

private fun DrawScope.drawThumb(thumb: AuroraScrollbarThumb, dragging: Boolean, active: Boolean, scrolling: Boolean) {
    val width = (if (dragging || active) AuroraLayout.ControlGap else AuroraLayout.SmallGap).toPx()
    val alpha = when {
        dragging -> 0.8f
        active -> 0.6f
        scrolling -> 0.3f
        else -> 36f / 255
    }
    drawRoundRect(
        color = AuroraColors.Ink.copy(alpha = alpha),
        topLeft = Offset((size.width - width) / 2, thumb.top),
        size = Size(width, thumb.length),
        cornerRadius = CornerRadius(width / 2),
    )
}
