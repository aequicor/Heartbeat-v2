@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.sandbox

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden-image check of the sandbox (HB-DS-040).
 *
 * The rendered screen is compared with the committed reference: a pixel counts as different when a
 * channel differs by more than the tolerance. The check fails when the share of different pixels
 * exceeds the global budget, and also when a local region differs more than its own budget, so a
 * small indicator, border or icon that disappears is not hidden by the size of the whole catalog
 * image. Run the tests with `HB_UPDATE_GOLDENS=1` to rewrite the references deliberately.
 *
 * Pixel comparison is the CI gate: the `sandbox-goldens` job runs it on the Windows image that
 * produced the references, so a visual regression fails the build instead of being skipped. A host
 * with a different font stack or Skia build passes `HB_GOLDEN_MODE=render` to render every scene and
 * run the interaction assertions without comparing pixels; the gate never disables itself just
 * because it runs in CI.
 */
internal object GoldenImage {

    fun verify(name: String, png: ByteArray) {
        val goldenFile = File(GoldenDirectory, "$name.png")
        val update = System.getenv(UpdateVariable) == "1"

        if (update) {
            goldenFile.parentFile.mkdirs()
            goldenFile.writeBytes(png)
            return
        }
        if (!goldenFile.exists()) {
            fail("Golden image $name is missing. Run the tests with $UpdateVariable=1 to create it.")
        }
        if (System.getenv(ModeVariable) == RenderOnlyMode) {
            return
        }

        val expected = ImageIO.read(goldenFile) ?: fail("Cannot read golden image $name")
        val actual = ImageIO.read(ByteArrayInputStream(png)) ?: fail("Cannot read rendered image $name")
        assertTrue(
            expected.width == actual.width && expected.height == actual.height,
            "Size of $name changed: ${expected.width}x${expected.height} -> ${actual.width}x${actual.height}",
        )

        val different = countDifferentPixels(expected, actual)
        val fraction = different.toDouble() / (expected.width * expected.height)
        assertTrue(
            fraction <= MaxDifferentFraction,
            "Visual regression in $name: ${(fraction * 100).format()} of pixels differ",
        )

        verifyRegions(expected, actual, name)
    }

    /**
     * Asserts that the rendered content does not touch the right edge of the scene: a catalog that
     * overflows horizontally is clipped and therefore unreachable, unlike the intentional vertical
     * scroll. The background is taken from the top-left corner, which stays inside the content
     * padding.
     */
    fun assertNoHorizontalClipping(name: String, png: ByteArray) {
        val image = ImageIO.read(ByteArrayInputStream(png)) ?: fail("Cannot read rendered image $name")
        val background = image.getRGB(0, 0)
        var rightmost = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (differs(background, image.getRGB(x, y))) {
                    rightmost = max(rightmost, x)
                }
            }
        }
        assertTrue(
            rightmost < image.width - EdgeMargin,
            "Content of $name is clipped horizontally: the rightmost content pixel is at x=$rightmost",
        )
    }

    private fun verifyRegions(expected: BufferedImage, actual: BufferedImage, name: String) {
        for (tileY in 0 until expected.height step RegionSize) {
            for (tileX in 0 until expected.width step RegionSize) {
                val width = min(RegionSize, expected.width - tileX)
                val height = min(RegionSize, expected.height - tileY)
                val different = countDifferentPixels(expected, actual, tileX, tileY, width, height)
                val fraction = different.toDouble() / (width * height)
                assertTrue(
                    fraction <= MaxRegionDifferentFraction,
                    "Visual regression in $name: the ${width}x$height region at ($tileX,$tileY) " +
                        "has ${(fraction * 100).format()} different pixels",
                )
            }
        }
    }

    private fun countDifferentPixels(expected: BufferedImage, actual: BufferedImage): Int =
        countDifferentPixels(expected, actual, 0, 0, expected.width, expected.height)

    private fun countDifferentPixels(
        expected: BufferedImage,
        actual: BufferedImage,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
    ): Int {
        var different = 0
        for (y in offsetY until offsetY + height) {
            for (x in offsetX until offsetX + width) {
                if (differs(expected.getRGB(x, y), actual.getRGB(x, y))) {
                    different++
                }
            }
        }
        return different
    }

    private fun differs(first: Int, second: Int): Boolean {
        val alpha = abs(((first shr 24) and ChannelMask) - ((second shr 24) and ChannelMask))
        val red = abs(((first shr 16) and ChannelMask) - ((second shr 16) and ChannelMask))
        val green = abs(((first shr 8) and ChannelMask) - ((second shr 8) and ChannelMask))
        val blue = abs((first and ChannelMask) - (second and ChannelMask))
        return alpha > ChannelTolerance ||
            red > ChannelTolerance ||
            green > ChannelTolerance ||
            blue > ChannelTolerance
    }

    private fun Double.format(): String = String.format(Locale.ROOT, "%.2f%%", this * 100.0)

    private const val GoldenDirectory = "src/jvmTest/resources/golden"

    private const val UpdateVariable = "HB_UPDATE_GOLDENS"

    private const val ModeVariable = "HB_GOLDEN_MODE"

    private const val RenderOnlyMode = "render"

    private const val ChannelMask = 0xFF

    private const val ChannelTolerance = 10

    private const val MaxDifferentFraction = 0.0005

    private const val RegionSize = 128

    private const val MaxRegionDifferentFraction = 0.01

    private const val EdgeMargin = 2
}
