@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.sandbox

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
 * Renders the adaptive classes at the documented widths (HB-DS-031). The scene is wider than the
 * widest sample, so the wide class is visible instead of being clipped by the sandbox canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
class AdaptiveSandboxScreenshotTest {

    @Test
    fun rendersAdaptiveWidths() {
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            HbTheme(appearance = HbAppearance.Light) {
                AdaptiveSandboxScreen()
            }
        }

        try {
            val data = scene.render().encodeToData(EncodedImageFormat.PNG)
            assertTrue(data != null, "PNG encoding failed")

            val directory = File("build/sandbox").apply { mkdirs() }
            val file = File(directory, "sandbox-adaptive-light.png")
            file.writeBytes(data.bytes)
            assertTrue(file.length() > 0, "Empty adaptive screenshot")
            GoldenImage.verify(name = "sandbox-adaptive-light", png = data.bytes)
        } finally {
            scene.close()
        }
    }

    private companion object {
        private const val WIDTH = 1800

        private const val HEIGHT = 500
    }
}
