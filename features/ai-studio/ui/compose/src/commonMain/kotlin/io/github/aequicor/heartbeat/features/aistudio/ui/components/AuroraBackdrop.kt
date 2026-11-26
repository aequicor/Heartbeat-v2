package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

internal class AuroraBackdrop(
    val colors: ImageBitmap,
    val pattern: ImageBitmap,
    val opaqueColors: Boolean = false,
)

internal suspend fun loadAuroraBackdrop(worker: CoroutineDispatcher = Dispatchers.Default): AuroraBackdrop =
    withContext(worker) {
        val colors = Res.readBytes("files/aurora-gradient.png").decodeToImageBitmap()
        val pattern = Res.readBytes("files/aurora-pattern.png").decodeToImageBitmap()
        AuroraBackdrop(colors, pattern, colors.isOpaque())
    }

private const val ArgbAlphaShift = 24
private const val OpaqueAlpha = 0xff
private const val BackdropResizeSettleMillis = 100L
private const val MaxBakedBackdropPixels = 4_194_304L // At most 16 MiB in ARGB_8888.

private fun ImageBitmap.isOpaque(): Boolean {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    return pixels.all { it ushr ArgbAlphaShift == OpaqueAlpha }
}


@Composable
internal fun Modifier.auroraBackdrop(
    backdrop: AuroraBackdrop?,
    bake: Boolean = true,
    worker: CoroutineDispatcher = Dispatchers.Default,
): Modifier {
    if (backdrop == null) {
        return drawBehind { drawRect(AuroraColors.ChatBackdrop) }
    }

    val tileWidth = with(LocalDensity.current) { AuroraLayout.PatternTile.roundToPx().coerceAtLeast(1) }
    val tile = remember(backdrop.pattern, tileWidth) {
        // Resample once per density. The repeating image shader draws the tile pixel for pixel.
        backdrop.pattern.resizePatternTile(tileWidth)
    }
    val patternPaint = remember(tile) { tile.patternPaint() }

    // A translucent gradient depends on the surface behind the viewport. Keep the direct painter
    // for that case, large viewports and intermediate sizes while the user is resizing.
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val canBake = bake && backdrop.opaqueColors
    val baking = remember { Mutex() }
    val ready by produceState<BakedBackdrop?>(null, backdrop, tile, viewport, canBake) {
        value = null
        if (canBake && viewport.isBakeable()) {
            val target = viewport
            delay(BackdropResizeSettleMillis)
            val bitmap = withContext(worker) {
                baking.withLock {
                    val result = ImageBitmap(target.width, target.height)
                    Canvas(result).drawBackdrop(
                        backdrop.colors, tile.patternPaint(), backdropGeometry(target),
                    )
                    result
                }
            }
            value = BakedBackdrop(backdrop, tile, target, bitmap)
        }
    }
    val bounds = if (canBake) onSizeChanged { viewport = it } else this
    return bounds.drawWithCache {
        val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val geometry = backdropGeometry(target)
        val colors = backdrop.colors
        onDrawBehind {
            val cached = ready
            if (cached != null && cached.matches(backdrop, tile, target)) {
                drawImage(cached.bitmap)
            } else {
                drawIntoCanvas { it.drawBackdrop(colors, patternPaint, geometry) }
            }
        }
    }
}

private class BakedBackdrop(
    private val backdrop: AuroraBackdrop,
    private val tile: ImageBitmap,
    private val size: IntSize,
    val bitmap: ImageBitmap,
) {
    fun matches(backdrop: AuroraBackdrop, tile: ImageBitmap, size: IntSize): Boolean =
        this.backdrop === backdrop && this.tile === tile && this.size == size
}

private fun IntSize.isBakeable(): Boolean =
    width > 0 && height > 0 && width.toLong() * height <= MaxBakedBackdropPixels

private class BackdropGeometry(val imageSize: IntSize, val patternBounds: Rect) {
    val imagePaint = Paint().apply { filterQuality = FilterQuality.Low }
}

private fun backdropGeometry(target: IntSize): BackdropGeometry =
    BackdropGeometry(target, Rect(0f, 0f, target.width.toFloat(), target.height.toFloat()))

private fun Canvas.drawBackdrop(colors: ImageBitmap, pattern: Paint, geometry: BackdropGeometry) {
    drawImageRect(colors, dstSize = geometry.imageSize, paint = geometry.imagePaint)
    drawRect(geometry.patternBounds, pattern)
}

private fun ImageBitmap.patternPaint(): Paint = Paint().apply {
    shader = ImageShader(this@patternPaint, TileMode.Repeated, TileMode.Repeated)
}

private fun ImageBitmap.resizePatternTile(tileWidth: Int): ImageBitmap {
    if (tileWidth == width) return this

    val tileHeight = (height * (tileWidth.toFloat() / width)).roundToInt().coerceAtLeast(1)
    val tile = ImageBitmap(tileWidth, tileHeight)
    Canvas(tile).drawImageRect(
        image = this,
        dstSize = IntSize(tileWidth, tileHeight),
        paint = Paint().apply { filterQuality = FilterQuality.Low },
    )
    return tile
}
