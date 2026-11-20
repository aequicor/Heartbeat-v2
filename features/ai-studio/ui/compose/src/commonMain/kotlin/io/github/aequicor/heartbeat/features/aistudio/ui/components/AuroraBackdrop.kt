package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

internal class AuroraBackdrop(val colors: ImageBitmap, val pattern: ImageBitmap)

internal suspend fun loadAuroraBackdrop(worker: CoroutineDispatcher = Dispatchers.Default): AuroraBackdrop =
    withContext(worker) {
        val colors = Res.readBytes("files/aurora-gradient.png").decodeToImageBitmap()
        val pattern = Res.readBytes("files/aurora-pattern.png").decodeToImageBitmap()
        AuroraBackdrop(colors, createPatternMask(pattern))
    }

private const val PatternOpacity = 0.22f

// A white base preserves the gradient through transparent texels. Modulate then matches
// Figma's Multiply over the opaque gradient, including on Android below API 29.
private fun createPatternMask(pattern: ImageBitmap): ImageBitmap {
    val mask = ImageBitmap(pattern.width, pattern.height)
    val canvas = Canvas(mask)
    val paint = Paint().apply { color = AuroraColors.ChatPatternMaskBase }
    canvas.drawRect(Rect(0f, 0f, mask.width.toFloat(), mask.height.toFloat()), paint)
    paint.alpha = PatternOpacity
    canvas.drawImage(pattern, Offset.Zero, paint)
    return mask
}

@Composable
internal fun Modifier.auroraBackdrop(backdrop: AuroraBackdrop?): Modifier {
    if (backdrop == null) {
        return drawBehind { drawRect(AuroraColors.ChatBackdrop) }
    }

    val tileWidth = with(LocalDensity.current) { AuroraLayout.PatternTile.roundToPx().coerceAtLeast(1) }
    val patternPaint = remember(backdrop.pattern, tileWidth) {
        // Resample once per density. The repeating image shader draws the tile pixel for pixel.
        val tile = backdrop.pattern.resizePatternTile(tileWidth)
        Paint().apply {
            shader = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
            blendMode = BlendMode.Modulate
        }
    }
    return drawWithCache {
        val colors = backdrop.colors
        val fillScale = maxOf(size.width / colors.width, size.height / colors.height)
        val targetSize = IntSize((colors.width * fillScale).roundToInt(), (colors.height * fillScale).roundToInt())
        val offset = IntOffset(
            ((size.width - targetSize.width) / 2f).roundToInt(),
            ((size.height - targetSize.height) / 2f).roundToInt(),
        )
        val patternBounds = Rect(Offset.Zero, size)
        onDrawBehind {
            drawImage(
                colors, dstOffset = offset, dstSize = targetSize,
                filterQuality = FilterQuality.Low,
            )
            drawIntoCanvas { it.drawRect(patternBounds, patternPaint) }
        }
    }
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
