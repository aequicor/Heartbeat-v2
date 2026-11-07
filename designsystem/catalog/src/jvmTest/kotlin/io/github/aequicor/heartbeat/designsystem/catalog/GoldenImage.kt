@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.catalog

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden-image check of the catalog (HB-DS-040).
 *
 * The rendered screen is compared with the committed reference: a pixel counts as different when a
 * channel differs by more than the tolerance, and the check fails when the share of different
 * pixels exceeds the budget. The budget absorbs platform antialiasing without hiding a real layout
 * change. Run the tests with `HB_UPDATE_GOLDENS=1` to rewrite the references deliberately.
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
    }

    private fun countDifferentPixels(expected: BufferedImage, actual: BufferedImage): Int {
        var different = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
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

    private const val ChannelMask = 0xFF

    private const val ChannelTolerance = 10

    private const val MaxDifferentFraction = 0.002
}
