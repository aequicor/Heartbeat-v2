@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraBackdrop
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuroraBackdropTest {
    @Test
    fun thinLinesSurviveDownsamplingAndRepeatOnBothAxes() {
        assertFilteredTile(1f, 720)
        assertFilteredTile(1.25f, 900)
    }

    @Test
    fun nativeTileResolutionPreservesSourcePixels() {
        renderBackdrop(2f, 1440) { pixels ->
            assertEquals(Color.White.toArgb(), pixels.getColor(0, 5))
            assertEquals(Color.Black.toArgb(), pixels.getColor(1, 5))
            assertEquals(Color.Black.toArgb(), pixels.getColor(1441, 5))
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
        val sourceSize = 1440
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
