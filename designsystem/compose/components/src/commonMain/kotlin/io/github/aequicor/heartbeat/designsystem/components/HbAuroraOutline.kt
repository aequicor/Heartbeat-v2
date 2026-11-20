package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout

/** Paints the outline and inset surface at their actual physical widths, including fractional scale. */
object HbAuroraOutline {
    operator fun invoke(
        background: Color,
        outline: Color,
        topRadius: Dp,
        bottomRadius: Dp = topRadius,
        stroke: Dp = HbAuroraLayout.Stroke,
        topEdge: Boolean = true,
    ): Modifier {
        val outerShape = auroraCorners(topRadius, bottomRadius)
        val innerShape = auroraCorners(
            (topRadius - stroke).coerceAtLeast(Dp.Hairline),
            (bottomRadius - stroke).coerceAtLeast(Dp.Hairline),
        )
        return Modifier.drawWithCache {
            val inset = stroke.toPx()
            val topInset = if (topEdge) inset else 0f
            val outer = outerShape.createOutline(size, layoutDirection, this)
            val innerSize = Size(size.width - inset * 2, size.height - inset - topInset)
            val inner = if (innerSize.width > 0 && innerSize.height > 0) {
                innerShape.createOutline(innerSize, layoutDirection, this)
            } else null
            onDrawBehind {
                drawOutline(outer, outline)
                if (inner != null) translate(inset, topInset) { drawOutline(inner, background) }
            }
        }.clip(outerShape)
    }

    /** Opens the top edge toward a header using the same progress as its opacity. */
    fun joiningHeader(background: Color, outline: Color, radius: Dp, joinProgress: () -> Float): Modifier =
        Modifier.drawWithCache {
            val closed = 1f - joinProgress().coerceIn(0f, 1f)
            val stroke = HbAuroraLayout.Stroke
            val inset = stroke.toPx()
            val topInset = inset * closed
            val topRadius = radius * closed
            val outer = auroraCorners(topRadius, radius).createOutline(size, layoutDirection, this)
            val innerSize = Size(size.width - inset * 2, size.height - inset - topInset)
            val inner = if (innerSize.width > 0 && innerSize.height > 0) {
                auroraCorners(
                    (topRadius - stroke).coerceAtLeast(Dp.Hairline),
                    (radius - stroke).coerceAtLeast(Dp.Hairline),
                ).createOutline(innerSize, layoutDirection, this)
            } else null
            val clip = Path().apply { addOutline(outer) }

            onDrawWithContent {
                drawOutline(outer, outline)
                if (inner != null) translate(inset, topInset) { drawOutline(inner, background) }
                clipPath(clip) { this@onDrawWithContent.drawContent() }
            }
        }
}

private fun auroraCorners(top: Dp, bottom: Dp) =
    RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
