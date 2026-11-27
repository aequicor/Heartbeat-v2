package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import kotlin.math.ceil
import kotlin.math.floor

/** The wallpaper stays sharp while history passes behind the floating controls. */
@Composable
internal fun Modifier.auroraHistoryEdges(
    headerHeightPx: Int,
    bottomHeightPx: Int,
    readingWidth: Dp,
): Modifier {
    val backdrop = LocalAuroraFloatingBackdrop.current
    val history = rememberGraphicsLayer()
    DisposableEffect(backdrop, history) {
        backdrop?.history = history
        onDispose {
            if (backdrop?.history === history) backdrop.history = null
        }
    }
    val topLayer = rememberGraphicsLayer()
    val bottomLayer = if (bottomHeightPx > 0) rememberGraphicsLayer() else null
    return drawWithCache {
        val radius = AuroraLayout.HeaderBlur.toPx()
        val transition = AuroraLayout.HeaderBlurTransition.toPx()
        val historyWidth = readingWidth.toPx()
        val recordingWidth = ceil((historyWidth + radius * 6).coerceAtMost(size.width)).toInt()
        val left = ((size.width - recordingWidth) / 2f).toInt()
        val right = (left + recordingWidth).toFloat()
        val topEnd = headerHeightPx.toFloat().coerceAtMost(size.height)
        val bottomStart = (size.height - bottomHeightPx).coerceAtLeast(topEnd)
        val top = AuroraHistoryBand(
            topLayer, Rect(left.toFloat(), 0f, right, topEnd),
            transition, topEdge = true, radius, size.height,
        )
        val bottom = bottomLayer?.let {
            AuroraHistoryBand(
                it, Rect(left.toFloat(), bottomStart, right, size.height),
                transition, topEdge = false, radius, size.height,
            )
        }
        val paint = Paint()
        val add = Paint().apply { blendMode = BlendMode.Plus }

        onDrawWithContent {
            history.record { this@onDrawWithContent.drawContent() }
            top.draw(this, history, paint, add)
            bottom?.draw(this, history, paint, add)
            clipRect(top = topEnd, bottom = bottomStart) { drawLayer(history) }
        }
    }
}

private class AuroraHistoryBand(
    private val layer: GraphicsLayer,
    private val bounds: Rect,
    transitionHeight: Float,
    topEdge: Boolean,
    radius: Float,
    viewportHeight: Float,
) {
    private val left = bounds.left.toInt()
    private val sampleTop = floor((bounds.top - radius * 3).coerceAtLeast(0f)).toInt()
    private val sampleBottom = ceil((bounds.bottom + radius * 3).coerceAtMost(viewportHeight)).toInt()
    private val recordingSize = IntSize(bounds.width.toInt(), sampleBottom - sampleTop)
    private val fadeStart = if (topEdge) (bounds.bottom - transitionHeight).coerceAtLeast(bounds.top) else bounds.top
    private val fadeEnd = if (topEdge) bounds.bottom else (bounds.top + transitionHeight).coerceAtMost(bounds.bottom)
    private val fadeBounds = Rect(bounds.left, fadeStart, bounds.right, fadeEnd)
    private val fadeIn = Brush.verticalGradient(
        listOf(Color.Transparent, AuroraColors.ContentMask), startY = fadeStart, endY = fadeEnd,
    )
    private val fadeOut = Brush.verticalGradient(
        listOf(AuroraColors.ContentMask, Color.Transparent), startY = fadeStart, endY = fadeEnd,
    )
    private val blurredMask = if (topEdge) fadeOut else fadeIn
    private val sharpMask = if (topEdge) fadeIn else fadeOut

    init {
        // The band is clipped after filtering. Clipping the recording layer can discard
        // its nested history on Desktop when Skia applies the render effect.
        layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        layer.topLeft = IntOffset(left, sampleTop)
    }

    fun draw(scope: DrawScope, history: GraphicsLayer, paint: Paint, add: Paint) = with(scope) {
        layer.record(size = recordingSize) {
            translate(left = -left.toFloat(), top = -sampleTop.toFloat()) { drawLayer(history) }
        }
        // Add complementary weights before compositing onto the wallpaper. Compositing each
        // pass separately would make even a fully opaque message translucent in the transition.
        withHistoryLayer(bounds, paint) {
            drawLayer(layer)
            drawRect(blurredMask, bounds.topLeft, bounds.size, blendMode = BlendMode.DstIn)
            withHistoryLayer(fadeBounds, add) {
                drawLayer(history)
                drawRect(sharpMask, fadeBounds.topLeft, fadeBounds.size, blendMode = BlendMode.DstIn)
            }
        }
    }
}

private inline fun DrawScope.withHistoryLayer(bounds: Rect, paint: Paint, draw: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, paint)
    try {
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom, block = draw)
    } finally {
        canvas.restore()
    }
}
