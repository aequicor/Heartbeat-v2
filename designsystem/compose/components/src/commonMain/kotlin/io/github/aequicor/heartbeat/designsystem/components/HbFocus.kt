package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Focus outline of the kit (HB-DS-012): a solid 2-unit border in the focus role, independent of
 * selection and validation styling. Components own the focus state and apply this modifier.
 */
internal fun Modifier.hbFocusRing(
    focused: Boolean,
    shape: Shape,
    color: Color,
): Modifier = if (focused) {
    border(width = HbComponentStates.focusedBorderWidth, color = color, shape = shape)
} else {
    this
}
