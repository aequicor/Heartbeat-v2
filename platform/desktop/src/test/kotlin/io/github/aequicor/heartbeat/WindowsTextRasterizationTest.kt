@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.InternalComposeUiApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class,
)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.theme.HbProvidePlatformTextStyle
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import java.awt.Container
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceProps
import org.jetbrains.skiko.SkiaLayer
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsTextRasterizationTest {
    @Test
    fun systemBodyKeepsCompactLineSpacingAndPlatformRasterization() {
        val style = configuredStyle(PixelGeometry.RGB_H, preferSubpixel = true)
        val rasterization = assertNotNull(style.paragraphStyle?.fontRasterizationSettings)
        assertEquals(FontHinting.Normal, rasterization.hinting)
        assertFalse(rasterization.subpixelPositioning)
        val family = FontFamily("Segoe UI")
        val recomposer = newFrameRecomposer()
        val scene = CanvasLayersComposeScene(recomposer, density = Density(1f), size = IntSize(400, 80))
        lateinit var layout: TextLayoutResult
        try {
            scene.setContent {
                HbProvidePlatformTextStyle(style) {
                    HbTheme(fontFamily = family) {
                        BasicText(
                            "Ah, you kids today with techno music!\n" +
                                "You should enjoy the classics, like\nHasselhoff!",
                            modifier = Modifier.graphicsLayer(),
                            style = HbTypography.Body.copy(color = Color.Black),
                            onTextLayout = { layout = it },
                        )
                    }
                }
            }
            val info = ImageInfo(400, 80, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            Surface.makeRaster(info, 0, SurfaceProps(PixelGeometry.RGB_H)).use { surface ->
                surface.canvas.clear(org.jetbrains.skia.Color.WHITE)
                recomposer.performFrame(0)
                scene.measureAndLayout()
                scene.draw(surface.canvas.asComposeCanvas())
                surface.makeImageSnapshot().use { image ->
                    val output = File("build/screenshots/windows-text-system-body.png")
                    output.parentFile.mkdirs()
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) }
                }
            }
            assertEquals(3, layout.lineCount)
            for (line in 0 until layout.lineCount) {
                assertTrue(layout.getLineRight(line) <= 400f, "The complete line fits its reading area")
                if (line > 0) {
                    assertEquals(22f, layout.getLineBaseline(line) - layout.getLineBaseline(line - 1), 0.1f)
                }
            }
        } finally {
            scene.close()
            recomposer.close()
        }
    }

    @Test
    fun defaultWindowTextUsesSubpixelEdgesForRgbAndBgrScreens() {
        Assume.assumeTrue(System.getenv("HEARTBEAT_WINDOWS_SUBPIXEL_TEXT") == null)
        for (geometry in listOf(PixelGeometry.RGB_H, PixelGeometry.BGR_H)) {
            val style = configuredStyle(geometry)
            assertEquals(FontSmoothing.SubpixelAntiAlias, style.paragraphStyle?.fontRasterizationSettings?.smoothing)
            verifyEdges("lcd-${geometry.name.lowercase()}", style, geometry, subpixel = true)
        }
    }

    @Test
    fun unknownScreenGeometryFallsBackToNeutralEdges() {
        val style = configuredStyle(geometry = null, preferSubpixel = true)
        verifyGrayscale("grayscale-unknown", style)
    }

    @Test
    fun explicitOptOutKeepsNeutralEdgesOnAnLcdScreen() {
        val style = configuredStyle(PixelGeometry.RGB_H, preferSubpixel = false)
        verifyGrayscale("grayscale-disabled", style)
    }

    @Test
    fun anAlreadyDisplayableWindowFallsBackToNeutralEdges() {
        val style = configuredStyle(PixelGeometry.RGB_H, preferSubpixel = true, displayable = true)
        verifyGrayscale("grayscale-displayable", style)
    }

    private fun newFrameRecomposer(dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined) =
        FrameRecomposer(dispatcher)

    private fun configuredStyle(
        geometry: PixelGeometry?,
        preferSubpixel: Boolean? = null,
        displayable: Boolean = false,
    ): PlatformTextStyle {
        Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        var style: PlatformTextStyle? = null
        SwingUtilities.invokeAndWait {
            val window = ComposeWindow()
            try {
                if (displayable) window.pack()
                style = if (preferSubpixel == null) {
                    configureWindowsText(window, pixelGeometry = geometry)
                } else {
                    configureWindowsText(window, preferSubpixel = preferSubpixel, pixelGeometry = geometry)
                }
                if (style?.paragraphStyle?.fontRasterizationSettings?.smoothing == FontSmoothing.SubpixelAntiAlias) {
                    val layer = window.contentPane.components.filterIsInstance<Container>()
                        .flatMap { it.components.asList() }.filterIsInstance<SkiaLayer>().single()
                    assertEquals(geometry, layer.pixelGeometry, "The window surface must use the selected geometry")
                }
            } finally {
                window.dispose()
            }
        }
        return assertNotNull(style, "Windows must receive an explicit text rasterization style")
    }

    private fun verifyGrayscale(mode: String, style: PlatformTextStyle) {
        assertEquals(FontSmoothing.AntiAlias, style.paragraphStyle?.fontRasterizationSettings?.smoothing)
        verifyEdges(mode, style, PixelGeometry.RGB_H, subpixel = false)
    }

    private fun verifyEdges(mode: String, style: PlatformTextStyle, geometry: PixelGeometry, subpixel: Boolean) {
        for (font in listOf("Segoe UI", "Consolas")) {
            for (scale in listOf(1f, 1.25f, 2f)) {
                renderText(mode, font, scale, style, geometry, subpixel)
            }
        }
    }

    private fun renderText(
        mode: String,
        font: String,
        scale: Float,
        style: PlatformTextStyle,
        geometry: PixelGeometry,
        subpixel: Boolean,
    ) {
        val family = FontFamily(font)
        val recomposer = newFrameRecomposer()
        val scene = CanvasLayersComposeScene(recomposer, density = Density(scale), size = IntSize(900, 80))
        try {
            scene.setContent {
                HbProvidePlatformTextStyle(style) {
                    HbTheme(fontFamily = family) {
                        BasicText(
                            "Heartbeat · Подготовка релиза · Поиск по проекту · 012345",
                            modifier = Modifier.graphicsLayer(),
                            style = HbTypography.Body.copy(
                                color = Color.Black,
                            ),
                        )
                    }
                }
            }
            val info = ImageInfo(900, 80, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            Surface.makeRaster(info, 0, SurfaceProps(geometry)).use { surface ->
                surface.canvas.clear(org.jetbrains.skia.Color.WHITE)
                recomposer.performFrame(0)
                scene.measureAndLayout()
                scene.draw(surface.canvas.asComposeCanvas())
                surface.makeImageSnapshot().use { image ->
                    val output = File("build/screenshots/windows-text-$mode-$font-$scale.png")
                    output.parentFile.mkdirs()
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) }
                    Bitmap.makeFromImage(image).use { bitmap ->
                        assertTextEdges(bitmap, "$mode: $font at $scale", subpixel)
                    }
                }
            }
        } finally {
            scene.close()
            recomposer.close()
        }
    }

    private fun assertTextEdges(bitmap: Bitmap, sample: String, subpixel: Boolean) {
        var ink = 0
        var colored = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getColor(x, y)
            val red = (pixel ushr 16) and 255
            val green = (pixel ushr 8) and 255
            val blue = pixel and 255
            if (red < 240 || green < 240 || blue < 240) ink++
            if (maxOf(red, green, blue) - minOf(red, green, blue) > 2) colored++
        }
        assertTrue(ink > 100, "$sample must draw visible text")
        if (subpixel) {
            assertTrue(colored > 100, "$sample must have LCD edges, got $colored")
        } else {
            assertEquals(0, colored, "$sample must have neutral edges")
        }
    }
}
