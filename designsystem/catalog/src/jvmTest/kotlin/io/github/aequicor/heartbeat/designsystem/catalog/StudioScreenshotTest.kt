@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.catalog

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the composed studio slice (HB-DS-031, HB-DS-032): navigation, session list, transcript,
 * approval, composer and inspector in one workspace, in both appearances.
 */
@OptIn(ExperimentalComposeUiApi::class)
class StudioScreenshotTest {

    @Test
    fun rendersStudioLight() {
        renderStudio("studio-light", HbAppearance.Light)
    }

    @Test
    fun rendersStudioDark() {
        renderStudio("studio-dark", HbAppearance.Dark)
    }

    private fun renderStudio(name: String, appearance: HbAppearance) {
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            HbTheme(appearance = appearance) {
                StudioCatalogScreen()
            }
        }

        try {
            val data = scene.render().encodeToData(EncodedImageFormat.PNG)
            assertTrue(data != null, "PNG encoding failed for $name")

            val directory = File("build/catalog").apply { mkdirs() }
            val file = File(directory, "$name.png")
            file.writeBytes(data.bytes)
            assertTrue(file.length() > 0, "Empty screenshot $name")
            GoldenImage.verify(name = name, png = data.bytes)
        } finally {
            scene.close()
        }
    }

    private companion object {
        private const val WIDTH = 1440

        private const val HEIGHT = 900
    }
}
