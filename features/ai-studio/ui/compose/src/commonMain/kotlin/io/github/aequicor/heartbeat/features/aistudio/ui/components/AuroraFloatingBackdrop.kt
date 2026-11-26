package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import kotlin.math.ceil

@Suppress("CompositionLocalAllowlist")
internal val LocalAuroraFloatingBackdrop = staticCompositionLocalOf<AuroraFloatingBackdrop?> { null }

@Composable
internal fun rememberAuroraFloatingBackdrop(): AuroraFloatingBackdrop {
    val wallpaper = rememberGraphicsLayer()
    return remember(wallpaper) { AuroraFloatingBackdrop(wallpaper) }
}

internal class AuroraFloatingBackdrop(private val wallpaper: GraphicsLayer) {

    var history by mutableStateOf<GraphicsLayer?>(null)

    private var wallpaperPosition by mutableStateOf<Offset?>(null)

    fun recordWallpaper(): Modifier = Modifier.onGloballyPositioned { wallpaperPosition = it.positionInWindow() }
        .drawWithCache {
            onDrawWithContent {
                wallpaper.record { this@onDrawWithContent.drawContent() }
                drawLayer(wallpaper)
            }
        }

    @Composable
    fun blurBehind(shape: Shape): Modifier {
        val sample = rememberGraphicsLayer()
        var panelPosition by remember { mutableStateOf<Offset?>(null) }
        return Modifier.onGloballyPositioned { panelPosition = it.positionInWindow() }.drawWithCache {
            val radius = Layout.FloatingPanelBlur.toPx()
            val effect = BlurEffect(radius, radius, TileMode.Clamp)
            if (!effect.isSupported()) return@drawWithCache onDrawBehind {}
            sample.renderEffect = effect
            val padding = ceil(radius * 3).toInt()
            val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val sampleSize = IntSize(ceil(size.width).toInt() + padding * 2, ceil(size.height).toInt() + padding * 2)

            onDrawBehind {
                val source = wallpaperPosition
                val panel = panelPosition
                // Window coordinates share an origin even when Popup uses another Compose root.
                if (source != null && panel != null) {
                    val offset = panel - source
                    val messageLayer = history
                    sample.record(size = sampleSize) {
                        translate(left = padding - offset.x, top = padding - offset.y) {
                            drawLayer(wallpaper)
                            if (messageLayer != null) drawLayer(messageLayer)
                        }
                    }
                    clipPath(clip) {
                        translate(left = -padding.toFloat(), top = -padding.toFloat()) { drawLayer(sample) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Modifier.auroraFloatingBlur(shape: Shape): Modifier =
    LocalAuroraFloatingBackdrop.current?.let { then(it.blurBehind(shape)) } ?: this

@Composable
internal fun Modifier.auroraMenuBackground(shape: Shape, fallback: Color): Modifier =
    then(auroraBlurredMenuBackground()?.invoke(shape) ?: Modifier.background(fallback, shape))

@Composable
internal fun auroraBlurredMenuBackground(): (@Composable (Shape) -> Modifier)? {
    val backdrop = LocalAuroraFloatingBackdrop.current ?: return null
    return { shape -> backdrop.blurBehind(shape).background(Colors.FloatingHeaderSurface, shape) }
}
