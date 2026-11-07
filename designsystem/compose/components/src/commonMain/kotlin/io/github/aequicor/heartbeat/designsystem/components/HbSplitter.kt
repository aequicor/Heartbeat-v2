@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens
import kotlin.math.roundToInt

/**
 * Resizable separator between panes (HB-DS-035).
 *
 * Dragging reports the delta in logical units through [onResize]; the same operation is available
 * from accessibility actions and from the arrow keys, and Home resets the arrangement when
 * [onReset] exists. The hit area is wider than the visible line, so the separator stays usable
 * without covering the adjacent content.
 */
@Composable
fun HbSplitter(
    orientation: HbOrientation,
    onResize: (Int) -> Unit,
    onReset: (() -> Unit)?,
    labels: HbSplitterLabels,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HbThemeTokens.colors
    var focused by remember { mutableStateOf(false) }
    val stepPixels = with(LocalDensity.current) { STEP.toPx() }.roundToInt()
    val vertical = orientation == HbOrientation.Vertical

    Box(
        modifier = modifier
            .then(
                if (vertical) {
                    Modifier.fillMaxHeight().width(HIT_THICKNESS)
                } else {
                    Modifier.fillMaxWidth().height(HIT_THICKNESS)
                },
            )
            .pointerInput(enabled, orientation) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = if (vertical) dragAmount.x else dragAmount.y
                    onResize(delta.roundToInt())
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .splitterKeys(
                enabled = enabled,
                stepPixels = stepPixels,
                onResize = onResize,
                onReset = onReset,
            )
            .splitterActions(
                enabled = enabled,
                labels = labels,
                stepPixels = stepPixels,
                onResize = onResize,
                onReset = onReset,
            )
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (vertical) {
                        Modifier.fillMaxHeight().width(LINE_THICKNESS)
                    } else {
                        Modifier.fillMaxWidth().height(LINE_THICKNESS)
                    },
                )
                .background(colors.border.subtle),
        )
    }
}

/** Arrow keys resize the pane, and Home restores the preferred arrangement. */
private fun Modifier.splitterKeys(
    enabled: Boolean,
    stepPixels: Int,
    onResize: (Int) -> Unit,
    onReset: (() -> Unit)?,
): Modifier = onKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.DirectionLeft, Key.DirectionUp -> {
                onResize(-stepPixels)
                true
            }

            Key.DirectionRight, Key.DirectionDown -> {
                onResize(stepPixels)
                true
            }

            Key.MoveHome -> {
                onReset?.invoke()
                true
            }

            else -> false
        }
}

/** Accessibility actions of the dragging alternative (HB-DS-035). */
private fun Modifier.splitterActions(
    enabled: Boolean,
    labels: HbSplitterLabels,
    stepPixels: Int,
    onResize: (Int) -> Unit,
    onReset: (() -> Unit)?,
): Modifier = semantics {
    if (enabled) {
        customActions = buildList {
            add(CustomAccessibilityAction(labels.narrower) { onResize(-stepPixels); true })
            add(CustomAccessibilityAction(labels.wider) { onResize(stepPixels); true })
            onReset?.let { reset -> add(CustomAccessibilityAction(labels.reset) { reset(); true }) }
        }
    }
}


private val HIT_THICKNESS = 8.dp

private val LINE_THICKNESS = 1.dp

private val STEP = 16.dp
