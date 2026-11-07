@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.catalog

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the catalog to PNG files for review (HB-DS-039, HB-DS-040).
 *
 * The scene is raster and offscreen, so the check runs without a desktop window. The images land in
 * `designsystem/compose/components/build/catalog`; both appearances, a compact width, the 150% text
 * scale and the high-contrast mapping are covered, and every image must be a non-empty PNG.
 */
@OptIn(ExperimentalComposeUiApi::class)
class CatalogScreenshotTest {

    @Test
    fun rendersWideLightCatalog() {
        renderCatalog("catalog-wide-light", WIDE_WIDTH, WIDE_HEIGHT, HbAppearance.Light)
    }

    @Test
    fun rendersWideDarkCatalog() {
        renderCatalog("catalog-wide-dark", WIDE_WIDTH, WIDE_HEIGHT, HbAppearance.Dark)
    }

    @Test
    fun rendersCompactLightCatalog() {
        renderCatalog("catalog-compact-light", COMPACT_WIDTH, COMPACT_HEIGHT, HbAppearance.Light)
    }

    @Test
    fun rendersHighContrastCatalog() {
        renderCatalog(
            name = "catalog-high-contrast",
            width = WIDE_WIDTH,
            height = WIDE_HEIGHT,
            appearance = HbAppearance.Light,
            contrast = HbContrastPreference.High,
        )
    }

    @Test
    fun rendersLargeTextCatalog() {
        renderCatalog(
            name = "catalog-large-text",
            width = WIDE_WIDTH,
            height = LARGE_TEXT_HEIGHT,
            appearance = HbAppearance.Light,
            textScale = 1.5f,
        )
    }

    private fun renderCatalog(
        name: String,
        width: Int,
        height: Int,
        appearance: HbAppearance,
        textScale: Float = 1f,
        contrast: HbContrastPreference = HbContrastPreference.System,
    ) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            HbTheme(
                appearance = appearance,
                textScale = textScale,
                contrast = contrast,
            ) {
                CatalogScreen()
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
        private const val WIDE_WIDTH = 1200

        private const val WIDE_HEIGHT = 2500

        private const val LARGE_TEXT_HEIGHT = 3400

        private const val COMPACT_WIDTH = 390

        private const val COMPACT_HEIGHT = 3100
    }
}
