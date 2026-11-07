@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.patterns.catalog

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
 * Renders the product patterns to PNG files for review (HB-DS-039, HB-DS-040): the runtime
 * states of a session, a message, a tool call, an approval request and a usage value, in both
 * appearances.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PatternsScreenshotTest {

    @Test
    fun rendersLightPatterns() {
        renderPatterns("patterns-light", HbAppearance.Light)
    }

    @Test
    fun rendersDarkPatterns() {
        renderPatterns("patterns-dark", HbAppearance.Dark)
    }

    private fun renderPatterns(name: String, appearance: HbAppearance) {
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            HbTheme(appearance = appearance) {
                PatternsCatalogScreen()
            }
        }

        try {
            val data = scene.render().encodeToData(EncodedImageFormat.PNG)
            assertTrue(data != null, "PNG encoding failed for $name")

            val directory = File("build/catalog").apply { mkdirs() }
            val file = File(directory, "$name.png")
            file.writeBytes(data.bytes)
            assertTrue(file.length() > 0, "Empty screenshot $name")
        } finally {
            scene.close()
        }
    }

    private companion object {
        private const val WIDTH = 900

        private const val HEIGHT = 2500
    }
}
