package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrast checks of the named token pairs (HB-DS-005). The palette carries the WCAG formulas, so
 * the contract is validated here instead of in a renderer test; the documented ratios are the
 * values recorded in `design/design-system.md`.
 */
class HbColorContrastTest {

    @Test
    fun primaryActionPairKeepsDocumentedContrast() {
        assertRatio(HbColorPalettes.Light, expected = 12.61)
        assertRatio(HbColorPalettes.Dark, expected = 10.45)
    }

    @Test
    fun primaryActionStatesStayReadable() {
        val light = HbColorPalettes.Light
        assertAboveContrast(
            expected = HbContrast.TextMinimum,
            actual = light.action.primaryHoverBackground.contrastRatio(light.action.primaryForeground),
        )
        assertAboveContrast(
            expected = HbContrast.TextMinimum,
            actual = light.action.primaryPressedBackground.contrastRatio(light.action.primaryForeground),
        )

        val dark = HbColorPalettes.Dark
        assertAboveContrast(
            expected = HbContrast.TextMinimum,
            actual = dark.action.primaryHoverBackground.contrastRatio(dark.action.primaryForeground),
        )
        assertAboveContrast(
            expected = HbContrast.TextMinimum,
            actual = dark.action.primaryPressedBackground.contrastRatio(dark.action.primaryForeground),
        )
    }

    @Test
    fun textRolesReachTextMinimumOnEverySurface() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { palette ->
            val surfaces = with(palette.surface) { listOf(canvas, base, subtle, raised, input) }
            val readableText = with(palette.text) { listOf(primary, secondary, muted) }
            surfaces.forEach { surface ->
                readableText.forEach { foreground ->
                    assertAboveContrast(HbContrast.TextMinimum, foreground.contrastRatio(surface))
                }
            }
        }
    }

    @Test
    fun mutedMetadataStaysReadableOnAuroraMessageAndWorkSurfaces() {
        val text = HbColorPalettes.Light.text
        assertEquals(HbColor.hex(0x20232A), text.primary)
        assertEquals(HbColor.hex(0x555D68), text.secondary)
        assertEquals(HbColor.hex(0x656D78), text.muted)
        assertTrue(text.muted != text.disabled, "Metadata must not use the disabled text role")
        listOf(HbColor.hex(0xFFFFFF), HbColor.hex(0xF1FEE1), HbColor.hex(0xF4F3F2)).forEach { surface ->
            assertAboveContrast(HbContrast.TextMinimum, text.muted.contrastRatio(surface))
            assertTrue(text.primary.contrastRatio(surface) > text.secondary.contrastRatio(surface))
            assertTrue(text.secondary.contrastRatio(surface) > text.muted.contrastRatio(surface))
        }
    }

    @Test
    fun controlBoundariesReachNonTextMinimum() {
        val light = HbColorPalettes.Light
        val dark = HbColorPalettes.Dark

        assertEquals(3.73, light.border.control.contrastRatio(light.surface.subtle), 0.01)
        assertEquals(3.48, dark.border.control.contrastRatio(dark.surface.raised), 0.01)
    }

    @Test
    fun selectionKeepsReadableForeground() {
        val light = HbColorPalettes.Light
        assertEquals(5.13, light.selection.foreground.contrastRatio(light.selection.background), 0.01)

        val dark = HbColorPalettes.Dark
        assertAboveContrast(
            expected = HbContrast.TextMinimum,
            actual = dark.selection.foreground.contrastRatio(dark.selection.background),
        )
    }

    @Test
    fun selectionIndicatorReachesNonTextMinimum() {
        // The selected container tint alone is below 3:1 against the surface, so the kit marks the
        // selection with an independent indicator; that indicator carries the contrast requirement.
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { palette ->
            listOf(palette.surface.base, palette.surface.subtle).forEach { surface ->
                assertAboveContrast(
                    expected = HbContrast.NonTextMinimum,
                    actual = palette.selection.indicator.contrastRatio(surface),
                )
            }
        }
    }

    @Test
    fun statusPairsReachTextMinimum() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { palette ->
            val statuses = listOf(
                palette.status.success,
                palette.status.warning,
                palette.status.error,
                palette.status.information,
            )
            statuses.forEach { status ->
                assertAboveContrast(
                    expected = HbContrast.TextMinimum,
                    actual = status.foreground.contrastRatio(status.container),
                )
            }
        }
    }

    @Test
    fun focusIsVisibleAgainstCanvas() {
        val light = HbColorPalettes.Light
        val dark = HbColorPalettes.Dark

        assertAboveContrast(
            expected = HbContrast.NonTextMinimum,
            actual = light.focus.contrastRatio(light.surface.canvas),
        )
        assertAboveContrast(
            expected = HbContrast.NonTextMinimum,
            actual = dark.focus.contrastRatio(dark.surface.canvas),
        )
    }

    @Test
    fun packedColorMatchesChannels() {
        val color = HbColor(0x24, 0x24, 0x26)

        assertEquals(0xFF242426L, color.toArgb())
        assertEquals(color, HbColor.hex(0x242426))
    }

    private fun assertRatio(scheme: HbColorScheme, expected: Double) {
        assertEquals(
            expected,
            scheme.action.primaryBackground.contrastRatio(scheme.action.primaryForeground),
            0.01,
        )
    }

    private fun assertAboveContrast(expected: Double, actual: Double) {
        assertTrue(
            actual >= expected,
            "Expected a contrast ratio of at least $expected, got $actual",
        )
    }
}
