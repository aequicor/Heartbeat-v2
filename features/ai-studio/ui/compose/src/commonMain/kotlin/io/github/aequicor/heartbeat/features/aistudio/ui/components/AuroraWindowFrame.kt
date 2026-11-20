package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout

// The notch needs its two curves in addition to the gap from the native buttons.
internal fun auroraCaptionClearance(overlap: Dp): Dp =
    if (overlap > Dp.Hairline) {
        overlap + AuroraLayout.WindowCaptionGap + AuroraLayout.WindowCutRadius * 2 + AuroraLayout.FrameStroke
    } else Dp.Hairline

@Composable
internal fun Modifier.auroraWindowFrame(captionClearance: Dp, captionHeight: Dp, captionOnLeft: Boolean): Modifier {
    val stroke = AuroraLayout.FrameStroke
    if (captionClearance <= Dp.Hairline) {
        val frame = remember {
            Brush.linearGradient(listOf(AuroraColors.Pearl, AuroraColors.Lavender, AuroraColors.Pearl))
        }
        val shape = RoundedCornerShape(AuroraLayout.FrameRadius)
        return clip(shape).background(AuroraColors.Pearl).border(stroke, frame, shape).padding(stroke).clip(shape)
    }
    val density = LocalDensity.current
    val join = captionHeight + AuroraLayout.WindowJoinBelowCaption
    val corner = AuroraLayout.WindowFrameRadius
    val cut = AuroraLayout.WindowCutRadius
    val outer = remember(captionClearance, join, captionOnLeft, density) {
        with(density) {
            cutFrame(captionClearance.toPx(), join.toPx(), corner.toPx(), cut.toPx(), captionOnLeft)
        }
    }
    val inner = remember(captionClearance, join, captionOnLeft, density) {
        with(density) {
            cutFrame(
                captionClearance.toPx(), join.toPx(), (corner - stroke).toPx(), cut.toPx(), captionOnLeft,
            )
        }
    }
    return clip(outer).auroraWindowOutline().padding(stroke).clip(inner)
}

@Suppress("MagicNumber")
internal fun Modifier.auroraTouchFilesFrame(): Modifier {
    val radius = AuroraLayout.TouchFilesFrameRadius
    val stroke = AuroraLayout.FrameStroke
    return fillMaxSize()
        .background(AuroraColors.DesktopCanvas)
        .padding(AuroraLayout.Space)
        .clip(RoundedCornerShape(radius))
        .drawWithCache {
            // Project the Figma gradient axis from normalized frame space into pixels.
            val axis = Offset(-0.7880f / size.width.coerceAtLeast(1f), -0.6157f / size.height.coerceAtLeast(1f))
            val unitAxis = axis / (axis.x * axis.x + axis.y * axis.y)
            val gradient = AuroraColors.windowFrame(start = unitAxis * -1.2018f, end = unitAxis * -0.2018f)
            onDrawBehind { drawRect(gradient) }
        }
        .padding(stroke)
        .clip(RoundedCornerShape(radius - stroke))
}

@Suppress("MagicNumber")
private fun Modifier.auroraWindowOutline(): Modifier = drawWithCache {
    val gradient = AuroraColors.windowFrame(
        start = Offset(size.width * 0.73f, 0f),
        end = Offset(size.width * 0.14f, size.height * 0.8f),
    )
    onDrawBehind { drawRect(gradient) }
}

@Suppress("MagicNumber")
private fun cutFrame(clearance: Float, join: Float, corner: Float, cutCorner: Float, captionOnLeft: Boolean): Shape =
    GenericShape { size, _ ->
        val width = size.width
        val height = size.height
        val depth = join.coerceAtMost(height / 2)
        val radius = corner.coerceAtMost(minOf(width / 4, (height - depth) / 2))
        val bend = cutCorner.coerceAtMost(minOf(depth / 2, width / 8))
        val cut = (width - clearance).coerceIn(radius + bend, width - radius - bend)

        moveTo(radius, 0f)
        lineTo(cut - bend, 0f)
        arcTo(Rect(cut - bend * 2, 0f, cut, bend * 2), -90f, 90f, false)
        lineTo(cut, depth - bend)
        arcTo(Rect(cut, depth - bend * 2, cut + bend * 2, depth), 180f, -90f, false)
        lineTo(width - radius, depth)
        arcTo(Rect(width - radius * 2, depth, width, depth + radius * 2), -90f, 90f, false)
        lineTo(width, height - radius)
        arcTo(Rect(width - radius * 2, height - radius * 2, width, height), 0f, 90f, false)
        lineTo(radius, height)
        arcTo(Rect(0f, height - radius * 2, radius * 2, height), 90f, 90f, false)
        lineTo(0f, radius)
        arcTo(Rect(0f, 0f, radius * 2, radius * 2), 180f, 90f, false)
        close()
        if (captionOnLeft) transform(Matrix().apply {
            translate(width, 0f)
            scale(-1f, 1f)
        })
    }
