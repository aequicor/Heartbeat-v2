package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout

@Composable
internal fun Modifier.auroraSidebarSurface(captionCut: Dp, captionHeight: Dp): Modifier {
    val radius = Layout.NavigationPanelRadius
    val hasCut = captionCut > Dp.Hairline
    val leftToRight = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val shape = remember(radius, captionCut, captionHeight, density) {
        if (hasCut) {
            val radiusPx = with(density) { radius.toPx() }
            val cutPx = with(density) { captionCut.toPx() }
            val heightPx = with(density) { captionHeight.toPx() }
            val ledgePx = with(density) { Layout.SmallGap.toPx() }
            GenericShape { size, direction ->
                sidebarEdge(size, radiusPx, cutPx, heightPx, ledgePx)
                lineTo(size.width, size.height)
                lineTo(size.width, 0f)
                close()
                if (direction == LayoutDirection.Rtl) mirror(size.width)
            }
        } else {
            RoundedCornerShape(topStart = radius, bottomStart = radius)
        }
    }
    return drawBehind {
        val underlayWidth = (captionCut + radius).toPx()
        drawRect(
            Colors.WorkspaceRail,
            topLeft = Offset(if (leftToRight) 0f else size.width - underlayWidth, 0f),
            size = Size(underlayWidth, size.height),
        )
    }.dropShadow(
        shape,
        Shadow(
            radius = Layout.Space,
            color = Colors.NavigationPanelShadow,
            offset = DpOffset(if (leftToRight) -Layout.ChromeInset else Layout.ChromeInset, Dp.Hairline),
        ),
    ).drawWithCache {
        val outer = shape.createOutline(size, layoutDirection, this)
        val inset = Layout.Stroke.toPx()
        if (hasCut) {
            val boundary = Path().apply { addOutline(outer) }
            val edge = Path().apply {
                sidebarEdge(size, radius.toPx(), captionCut.toPx(), captionHeight.toPx(), Layout.SmallGap.toPx())
                if (!leftToRight) mirror(size.width)
            }
            val height = size.height.coerceAtLeast(1f)
            val solid = (inset * 2 / height).coerceAtMost(0.5f)
            val taperStart = (1f - Layout.NavigationOutlineTaper.toPx() / height).coerceAtLeast(solid)
            val ink = Colors.NavigationPanelOutline
            // Ease the outline into the window edge where the first arc meets the top.
            val outline = Brush.verticalGradient(
                0f to ink.copy(alpha = 0f),
                solid to ink,
                taperStart to ink,
                1f to ink.copy(alpha = 0f),
                endY = height,
            )
            onDrawBehind {
                drawOutline(outer, Colors.DesktopCanvas)
                clipPath(boundary) { drawPath(edge, outline, style = Stroke(width = inset * 2)) }
            }
        } else {
            val fill = shape.createOutline(Size((size.width - inset).coerceAtLeast(0f), size.height), layoutDirection, this)
            onDrawBehind {
                drawOutline(outer, Colors.NavigationPanelOutline)
                translate(left = if (leftToRight) inset else 0f) {
                    drawOutline(fill, Colors.DesktopCanvas)
                }
            }
        }
    }
}

private fun Path.sidebarEdge(size: Size, radius: Float, captionCut: Float, captionHeight: Float, ledgeWidth: Float) {
    val outer = minOf(radius, size.height / 4, size.width / 4, captionHeight / 2)
    val cut = captionCut.coerceAtMost(size.width - outer)
    val bend = minOf(outer, (cut - ledgeWidth).coerceAtLeast(0f) / 2)
    val shelf = captionHeight.coerceIn(outer + bend, size.height - outer - bend)

    moveTo(cut + outer, 0f)
    arcTo(Rect(cut, 0f, cut + outer * 2, outer * 2), -90f, -90f, forceMoveTo = false)
    lineTo(cut, shelf - bend)
    if (bend > 0f) {
        arcTo(Rect(cut - bend * 2, shelf - bend * 2, cut, shelf), 0f, 90f, forceMoveTo = false)
        lineTo(bend, shelf)
        arcTo(Rect(0f, shelf, bend * 2, shelf + bend * 2), -90f, -90f, forceMoveTo = false)
    } else {
        lineTo(cut, shelf)
        lineTo(0f, shelf)
    }
    lineTo(0f, size.height - outer)
    arcTo(Rect(0f, size.height - outer * 2, outer * 2, size.height), 180f, -90f, forceMoveTo = false)
}

private fun Path.mirror(width: Float) {
    transform(Matrix().apply {
        translate(width, 0f)
        scale(-1f, 1f)
    })
}
