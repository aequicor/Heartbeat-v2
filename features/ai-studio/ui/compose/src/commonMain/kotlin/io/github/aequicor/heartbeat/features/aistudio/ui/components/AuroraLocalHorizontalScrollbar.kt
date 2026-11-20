@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout

@Composable
internal fun AuroraLocalHorizontalScrollbar(
    scroll: ScrollState,
    touch: Boolean,
    modifier: Modifier = Modifier,
) {
    if (scroll.viewportSize <= 0 || (!scroll.canScrollForward && !scroll.canScrollBackward)) return

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = hovered || focused
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val minimum = with(LocalDensity.current) { AuroraLayout.ScrollbarMinThumb.toPx() }
    var trackWidth by remember { mutableIntStateOf(0) }
    val keyboard = if (touch) Modifier else Modifier
        .horizontalScrollKeys(scroll, direction)
        .hoverable(interaction)
        .focusable(interactionSource = interaction)
        .draggable(
            rememberDraggableState { delta ->
                val travel = trackWidth - horizontalThumbWidth(scroll, trackWidth.toFloat(), minimum)
                if (travel > 0) scroll.dispatchRawDelta(delta * scroll.maxValue / travel * direction)
            },
            Orientation.Horizontal,
            interactionSource = interaction,
        )
    Canvas(
        modifier.fillMaxWidth().height(AuroraLayout.NavigationTrack)
            .onSizeChanged { trackWidth = it.width }.then(keyboard),
    ) {
        val width = horizontalThumbWidth(scroll, size.width, minimum)
        val thickness = when {
            dragged -> AuroraLayout.Space
            active -> AuroraLayout.ControlGap
            else -> AuroraLayout.SmallGap
        }.toPx()
        val progress = scroll.value / scroll.maxValue.coerceAtLeast(1).toFloat()
        val alpha = scrollbarAlpha(dragged, active, touch && scroll.isScrollInProgress)
        val orientedProgress = if (direction > 0f) progress else 1f - progress
        drawRoundRect(
            color = AuroraColors.Ink.copy(alpha = alpha),
            topLeft = Offset((size.width - width) * orientedProgress, (size.height - thickness) / 2),
            size = Size(width, thickness),
            cornerRadius = CornerRadius(thickness / 2),
        )
    }
}

private fun horizontalThumbWidth(scroll: ScrollState, track: Float, minimum: Float): Float {
    val total = scroll.viewportSize.toFloat() + scroll.maxValue
    return if (total == 0f) track else
        (track * scroll.viewportSize / total).coerceIn(minimum.coerceAtMost(track), track)
}

private fun Modifier.horizontalScrollKeys(scroll: ScrollState, direction: Float) = this
    .semantics { contentDescription = "Горизонтальная прокрутка" }
    .onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val step = scroll.viewportSize / 8f * direction
        when (event.key) {
            Key.DirectionLeft -> scroll.dispatchRawDelta(-step) != 0f
            Key.DirectionRight -> scroll.dispatchRawDelta(step) != 0f
            else -> false
        }
    }

private fun scrollbarAlpha(dragging: Boolean, active: Boolean, scrolling: Boolean): Float = when {
    dragging -> .8f
    active -> .6f
    scrolling -> .3f
    else -> 36f / 255
}
