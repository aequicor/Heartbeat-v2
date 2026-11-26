@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraHistoryEdges
import io.github.aequicor.heartbeat.features.aistudio.ui.components.loadAuroraBackdrop
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuroraBackdropTest {
    @Test
    fun thinLinesSurviveDownsamplingAndRepeatOnBothAxes() {
        assertFilteredTile(1f, 640)
        assertFilteredTile(1.25f, 800)
    }

    @Test
    fun nativeTileResolutionPreservesSourcePixels() {
        renderBackdrop(2f, 1280) { pixels ->
            assertEquals(Color.White.toArgb(), pixels.getColor(0, 5))
            assertEquals(Color.Black.toArgb(), pixels.getColor(1, 5))
            assertEquals(Color.Black.toArgb(), pixels.getColor(1281, 5))
        }
    }

    @Test
    fun bakedWallpaperKeepsEveryPixelAcrossTheHistoryMask() = runBlocking {
        val backdrop = loadAuroraBackdrop()
        assertTrue(backdrop.opaqueColors, "The production wallpaper is eligible for offscreen baking")
        compareWallpaper(backdrop, 1440, 900, 1f, bottomBand = 84, label = "desktop")
        compareWallpaper(backdrop, 1080, 2400, 2.75f, bottomBand = 0, label = "android-density")
    }

    private fun compareWallpaper(
        backdrop: AuroraBackdrop,
        width: Int,
        height: Int,
        density: Float,
        bottomBand: Int,
        label: String,
    ) {
        val offset = mutableIntStateOf(0)
        val clock = AuroraHoverTestClock()
        fun scene(bake: Boolean) = ImageComposeScene(
            width, height, density = Density(density), coroutineContext = clock.context,
        ) {
            Box(Modifier.fillMaxSize().auroraBackdrop(backdrop, bake, clock.context)) {
                ComposeCanvas(
                    Modifier.fillMaxSize().auroraHistoryEdges(
                        (100 * density).toInt(), bottomBand, (width / density).dp,
                    ),
                ) {
                    val rowHeight = 88.dp.toPx()
                    val position = offset.intValue.dp.toPx()
                    repeat((size.height / rowHeight).toInt() + 2) { row ->
                        val y = row * rowHeight - position % rowHeight
                        drawRect(
                            if (row % 2 == 0) Color.White else Color(0xFFEFF8E5),
                            Offset((width * if (row % 2 == 0) .17f else .33f), y),
                            Size(width * .48f, rowHeight * .75f),
                        )
                    }
                }
            }
        }

        val direct = scene(false)
        val baked = scene(true)
        try {
            clock.frame(direct)
            clock.frame(baked)
            clock.advance(baked, 200)
            val screenshots = File("build/screenshots").apply { mkdirs() }
            fun screenshot(scene: ImageComposeScene, suffix: String): IntArray {
                val file = File(screenshots, "aurora-wallpaper-$label-$suffix.png")
                scene.render(clock.nanoTime).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)?.use { file.writeBytes(it.bytes) }
                }
                return ImageIO.read(file).getRGB(0, 0, width, height, null, 0, width)
            }
            assertTrue(
                screenshot(direct, "direct").contentEquals(screenshot(baked, "baked")),
                "Wallpaper composition must be pixel-identical in the sharp and masked areas at $label",
            )
            if (System.getenv("HEARTBEAT_PROFILE_BACKDROP") == "1") {
                repeat(8) {
                    offset.intValue = it * 3
                    direct.render(clock.nanoTime).close()
                    baked.render(clock.nanoTime).close()
                }
                val original = mutableListOf<Long>()
                val optimized = mutableListOf<Long>()
                repeat(32) { frame ->
                    offset.intValue = (frame + 8) * 3
                    if (frame % 2 == 0) {
                        original += measureNanoTime { direct.render(clock.nanoTime).close() }
                        optimized += measureNanoTime { baked.render(clock.nanoTime).close() }
                    } else {
                        optimized += measureNanoTime { baked.render(clock.nanoTime).close() }
                        original += measureNanoTime { direct.render(clock.nanoTime).close() }
                    }
                }
                fun List<Long>.percentile(fraction: Double) = sorted()[(lastIndex * fraction).toInt()] / 1_000_000.0
                File(screenshots, "aurora-wallpaper-$label-timing.txt").writeText(
                    "32 software-rendered frames per variant, alternating order\n" +
                        "Direct p50=${original.percentile(.5)} ms, p95=${original.percentile(.95)} ms\n" +
                        "Baked p50=${optimized.percentile(.5)} ms, p95=${optimized.percentile(.95)} ms\n",
                )
            }
        } finally {
            baked.close()
            direct.close()
        }
    }

    private fun assertFilteredTile(density: Float, tilePixels: Int) {
        renderBackdrop(density, tilePixels) { pixels ->
            val verticalLine = pixels.getColor(0, 5)
            val horizontalLine = pixels.getColor(5, 0)
            assertTrue((verticalLine and 0xff) in 1..254, "Expected partial coverage at density $density")
            assertEquals(verticalLine, horizontalLine)
            assertEquals(verticalLine, pixels.getColor(tilePixels, 5))
            assertEquals(horizontalLine, pixels.getColor(5, tilePixels))
            assertEquals(Color.White.toArgb(), pixels.getColor(tilePixels - 1, 5))
            assertEquals(Color.White.toArgb(), pixels.getColor(5, tilePixels - 1))
        }
    }

    private fun renderBackdrop(density: Float, tilePixels: Int, verify: (Bitmap) -> Unit) {
        val sourceSize = 1280
        val white = Paint().apply { color = Color.White }
        val ink = Paint().apply { color = Color.Black }
        val gradient = ImageBitmap(1, 1)
        Canvas(gradient).drawRect(Rect(0f, 0f, 1f, 1f), white)
        val pattern = ImageBitmap(sourceSize, sourceSize)
        with(Canvas(pattern)) {
            val edge = sourceSize.toFloat()
            drawRect(Rect(0f, 0f, edge, edge), white)
            drawRect(Rect(1f, 0f, 2f, edge), ink)
            drawRect(Rect(0f, 1f, edge, 2f), ink)
        }
        val backdrop = AuroraBackdrop(gradient, pattern)
        val scene = ImageComposeScene(tilePixels + 6, tilePixels + 6, density = Density(density)) {
            Box(Modifier.fillMaxSize().auroraBackdrop(backdrop))
        }
        try {
            scene.render().use { image -> Bitmap.makeFromImage(image).use(verify) }
        } finally {
            scene.close()
        }
    }
}
