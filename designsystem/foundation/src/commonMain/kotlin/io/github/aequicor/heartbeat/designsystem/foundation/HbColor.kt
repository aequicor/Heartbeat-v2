package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Color of the portable token contract: sRGB with an explicit alpha channel, without renderer
 * types. A Compose, SwiftUI or Win32 adapter converts it through [toArgb] or its channel values.
 *
 * Contrast helpers implement the WCAG relative luminance and contrast ratio formulas so the
 * palette is checked by the token contract itself instead of by a renderer test.
 */
public data class HbColor(
    public val red: Int,
    public val green: Int,
    public val blue: Int,
    public val alpha: Int = OPAQUE_ALPHA,
) {
    init {
        require(red in CHANNEL_RANGE) { "Red channel is out of range: $red" }
        require(green in CHANNEL_RANGE) { "Green channel is out of range: $green" }
        require(blue in CHANNEL_RANGE) { "Blue channel is out of range: $blue" }
        require(alpha in CHANNEL_RANGE) { "Alpha channel is out of range: $alpha" }
    }

    /** Packed `0xAARRGGBB`, the value a renderer adapter resolves into its own color type. */
    public fun toArgb(): Long =
        (alpha.toLong() shl ALPHA_SHIFT) or
            (red.toLong() shl RED_SHIFT) or
            (green.toLong() shl GREEN_SHIFT) or
            blue.toLong()

    /** Composites this color over an opaque [background] and returns the resulting opaque color. */
    public fun compositedOver(background: HbColor): HbColor {
        require(background.alpha == OPAQUE_ALPHA) { "The background must be opaque." }
        if (alpha == OPAQUE_ALPHA) return this
        val source = alpha / OPAQUE_ALPHA.toDouble()
        return HbColor(
            red = blendChannel(red, background.red, source),
            green = blendChannel(green, background.green, source),
            blue = blendChannel(blue, background.blue, source),
        )
    }

    /** WCAG 2.2 relative luminance in the range 0 to 1. */
    public fun relativeLuminance(): Double =
        LUMINANCE_RED * linearChannel(red) +
            LUMINANCE_GREEN * linearChannel(green) +
            LUMINANCE_BLUE * linearChannel(blue)

    /**
     * WCAG 2.2 contrast ratio against [other], from 1 to 21; the argument order does not matter.
     *
     * Both colors must be opaque: WCAG defines contrast for composited colors, so a translucent
     * color is rejected instead of returning a value that ignores its alpha. Composite it first with
     * [compositedOver] using the known background.
     */
    public fun contrastRatio(other: HbColor): Double {
        require(alpha == OPAQUE_ALPHA && other.alpha == OPAQUE_ALPHA) {
            "Contrast requires opaque colors; composite a translucent color over its background first."
        }
        val first = relativeLuminance()
        val second = other.relativeLuminance()
        return (max(first, second) + CONTRAST_OFFSET) / (min(first, second) + CONTRAST_OFFSET)
    }

    public companion object {

        private const val OPAQUE_ALPHA = 255

        private const val MAX_CHANNEL = 255

        private val CHANNEL_RANGE = 0..MAX_CHANNEL

        private const val ALPHA_SHIFT = 24

        private const val RED_SHIFT = 16

        private const val GREEN_SHIFT = 8

        private const val SRGB_LINEAR_THRESHOLD = 0.04045

        private const val SRGB_SLOPE = 12.92

        private const val SRGB_OFFSET = 0.055

        private const val SRGB_SCALE = 1.055

        private const val SRGB_EXPONENT = 2.4

        private const val LUMINANCE_RED = 0.2126

        private const val LUMINANCE_GREEN = 0.7152

        private const val LUMINANCE_BLUE = 0.0722

        private const val CONTRAST_OFFSET = 0.05

        /** Opaque color from a `0xRRGGBB` literal, the form the palette tables use. */
        public fun hex(rgb: Long): HbColor = HbColor(
            red = (rgb shr RED_SHIFT).toInt() and MAX_CHANNEL,
            green = (rgb shr GREEN_SHIFT).toInt() and MAX_CHANNEL,
            blue = rgb.toInt() and MAX_CHANNEL,
        )

        private fun blendChannel(source: Int, background: Int, alpha: Double): Int =
            (source * alpha + background * (1 - alpha)).roundToInt().coerceIn(CHANNEL_RANGE)

        private fun linearChannel(channel: Int): Double {
            val value = channel / MAX_CHANNEL.toDouble()
            return if (value <= SRGB_LINEAR_THRESHOLD) {
                value / SRGB_SLOPE
            } else {
                ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
            }
        }
    }
}
