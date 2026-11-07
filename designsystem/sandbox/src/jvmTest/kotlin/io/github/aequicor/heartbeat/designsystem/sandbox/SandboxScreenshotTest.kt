@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the sandbox to PNG files for review (HB-DS-039, HB-DS-040).
 *
 * The scene is raster and offscreen, so the check runs without a desktop window. The images land in
 * `designsystem/sandbox/build/sandbox`; both appearances, a compact width, the 150% text scale, the
 * high-contrast mapping and the touch density are covered, and every image must be a non-empty PNG.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SandboxScreenshotTest {

    @Test
    fun rendersWideLightSandbox() {
        renderSandbox("sandbox-wide-light", WIDE_WIDTH, WIDE_HEIGHT, HbAppearance.Light)
    }

    @Test
    fun rendersWideDarkSandbox() {
        renderSandbox("sandbox-wide-dark", WIDE_WIDTH, WIDE_HEIGHT, HbAppearance.Dark)
    }

    @Test
    fun rendersCompactLightSandbox() {
        renderSandbox("sandbox-compact-light", COMPACT_WIDTH, COMPACT_HEIGHT, HbAppearance.Light)
    }

    @Test
    fun rendersTouchSandbox() {
        renderSandbox(
            name = "sandbox-touch",
            width = WIDE_WIDTH,
            height = TOUCH_HEIGHT,
            appearance = HbAppearance.Light,
            density = HbDensity.Touch,
        )
    }

    @Test
    fun rendersHighContrastSandbox() {
        renderSandbox(
            name = "sandbox-high-contrast",
            width = WIDE_WIDTH,
            height = WIDE_HEIGHT,
            appearance = HbAppearance.Light,
            contrast = HbContrastPreference.High,
        )
    }

    @Test
    fun rendersLargeTextSandbox() {
        renderSandbox(
            name = "sandbox-large-text",
            width = WIDE_WIDTH,
            height = LARGE_TEXT_HEIGHT,
            appearance = HbAppearance.Light,
            textScale = 1.5f,
        )
    }

    private fun renderSandbox(
        name: String,
        width: Int,
        height: Int,
        appearance: HbAppearance,
        textScale: Float = 1f,
        contrast: HbContrastPreference = HbContrastPreference.System,
        density: HbDensity = HbDensity.Dense,
    ) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            HbTheme(
                appearance = appearance,
                textScale = textScale,
                contrast = contrast,
                density = density,
            ) {
                SandboxScreen()
            }
        }

        try {
            val data = scene.render().encodeToData(EncodedImageFormat.PNG)
            assertTrue(data != null, "PNG encoding failed for $name")

            val directory = File("build/sandbox").apply { mkdirs() }
            val file = File(directory, "$name.png")
            file.writeBytes(data.bytes)
            assertTrue(file.length() > 0, "Empty screenshot $name")
            GoldenImage.verify(name = name, png = data.bytes)
        } finally {
            scene.close()
        }
    }

    private companion object {
        private const val WIDE_WIDTH = 1200

        private const val WIDE_HEIGHT = 2750

        private const val LARGE_TEXT_HEIGHT = 3650

        private const val COMPACT_WIDTH = 390

        private const val COMPACT_HEIGHT = 3350

        private const val TOUCH_HEIGHT = 3250
    }
}
