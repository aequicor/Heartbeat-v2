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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.math.roundToInt

/**
 * Resizable separator between panes (HB-DS-035).
 *
 * Dragging reports the delta in logical units through [onResize]; the same operation is available
 * from accessibility actions and from the arrow keys, and Home resets the arrangement when
 * [onReset] exists. The separator occupies its density minimum target in the layout and paints a
 * one-unit line inside it: the hit area is a real layout region, so it never overlaps the panes
 * whose minimum widths the fit policy counts.
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
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val currentOnResize by rememberUpdatedState(onResize)
    val step = STEP.value.roundToInt()
    val vertical = orientation == HbOrientation.Vertical

    Box(
        modifier = modifier
            .then(
                if (vertical) {
                    Modifier.fillMaxHeight().width(HbSizes.minimumTarget)
                } else {
                    Modifier.fillMaxWidth().height(HbSizes.minimumTarget)
                },
            )
            .pointerInput(enabled, orientation) {
                if (!enabled) return@pointerInput
                // A pointer delta is fractional on a high-density host: accumulate it and report
                // only whole logical units, so the pane does not lose sub-unit movement.
                var remainder = 0f
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaPx = if (vertical) dragAmount.x else dragAmount.y
                    val delta = remainder + deltaPx / density
                    val whole = delta.roundToInt()
                    remainder = delta - whole
                    if (whole != 0) currentOnResize(whole)
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .splitterKeys(
                enabled = enabled,
                vertical = vertical,
                step = step,
                onResize = currentOnResize,
                onReset = onReset,
            )
            .splitterActions(
                enabled = enabled,
                labels = labels,
                step = step,
                onResize = currentOnResize,
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

/** Arrow keys of the orientation resize the pane, and Home restores the preferred arrangement. */
private fun Modifier.splitterKeys(
    enabled: Boolean,
    vertical: Boolean,
    step: Int,
    onResize: (Int) -> Unit,
    onReset: (() -> Unit)?,
): Modifier = onKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val narrowKey = if (vertical) Key.DirectionLeft else Key.DirectionUp
        val widenKey = if (vertical) Key.DirectionRight else Key.DirectionDown
        when (event.key) {
            narrowKey -> {
                onResize(-step)
                true
            }

            widenKey -> {
                onResize(step)
                true
            }

            Key.MoveHome -> {
                onReset?.invoke()
                onReset != null
            }

            else -> false
        }
}

/** Accessibility actions of the dragging alternative (HB-DS-035). */
private fun Modifier.splitterActions(
    enabled: Boolean,
    labels: HbSplitterLabels,
    step: Int,
    onResize: (Int) -> Unit,
    onReset: (() -> Unit)?,
): Modifier = semantics {
    if (enabled) {
        customActions = buildList {
            add(CustomAccessibilityAction(labels.narrower) { onResize(-step); true })
            add(CustomAccessibilityAction(labels.wider) { onResize(step); true })
            onReset?.let { reset -> add(CustomAccessibilityAction(labels.reset) { reset(); true }) }
        }
    }
}


private val LINE_THICKNESS = 1.dp

private val STEP = 16.dp
