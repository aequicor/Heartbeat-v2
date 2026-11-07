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
 * Renders the workspace scaffold at the compact, medium and expanded widths (HB-DS-031). The image
 * shows which regions survive the fit policy instead of a schematic of the classes.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScaffoldScreenshotTest {

    @Test
    fun rendersWorkspaceScaffolds() {
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            HbTheme(appearance = HbAppearance.Light) {
                ScaffoldCatalogScreen()
            }
        }

        try {
            val data = scene.render().encodeToData(EncodedImageFormat.PNG)
            assertTrue(data != null, "PNG encoding failed")

            val directory = File("build/catalog").apply { mkdirs() }
            val file = File(directory, "patterns-scaffold-light.png")
            file.writeBytes(data.bytes)
            assertTrue(file.length() > 0, "Empty scaffold screenshot")
        } finally {
            scene.close()
        }
    }

    private companion object {
        private const val WIDTH = 1400

        private const val HEIGHT = 620
    }
}
