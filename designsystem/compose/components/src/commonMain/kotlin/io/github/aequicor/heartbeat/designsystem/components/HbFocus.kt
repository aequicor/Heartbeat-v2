package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.translate


/**
 * Focus indicator of the kit (HB-DS-012, HB-DS-005).
 *
 * The indicator is a separate 2-unit outline with a 2-unit gap outside the control, so it never
 * replaces the validation or selection border and stays visible when the control is invalid, selected
 * or filled. It is drawn outside the measured bounds, over the surrounding surface, which is why the
 * focus role is checked against the canvas and action backgrounds.
 */
internal fun Modifier.hbFocusRing(
    focused: Boolean,
    shape: Shape,
    color: Color,
): Modifier = if (!focused) {
    this
} else {
    drawBehind {
        val widthPx = HbComponentStates.focusedBorderWidth.toPx()
        val inset = HbComponentStates.focusGap.toPx() + widthPx / 2f
        val outline = shape.createOutline(
            size = Size(size.width + inset * 2f, size.height + inset * 2f),
            layoutDirection = layoutDirection,
            density = this,
        )
        translate(left = -inset, top = -inset) {
            val path = Path().apply { addOutline(outline) }
            drawPath(path = path, color = color, style = Stroke(width = widthPx))
        }
    }
}
