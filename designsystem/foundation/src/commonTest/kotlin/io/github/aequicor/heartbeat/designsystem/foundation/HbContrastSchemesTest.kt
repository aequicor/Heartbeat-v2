package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * High-contrast mapping checks (HB-DS-005): secondary text and boundaries are strengthened, the
 * disabled role stays distinct, and the validated action and status pairs are unchanged.
 */
class HbContrastSchemesTest {

    @Test
    fun secondaryTextCollapsesOntoPrimary() {
        val mapped = HbContrastSchemes.highContrast(HbColorPalettes.Light)

        assertEquals(HbColorPalettes.Light.text.primary, mapped.text.secondary)
    }

    @Test
    fun boundariesUseTheStrongerRoles() {
        val base = HbColorPalettes.Light
        val mapped = HbContrastSchemes.highContrast(base)

        assertEquals(base.border.control, mapped.border.subtle)
        assertEquals(base.text.secondary, mapped.border.control)
    }

    @Test
    fun boundaryContrastReachesTextMinimum() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { base ->
            val mapped = HbContrastSchemes.highContrast(base)
            assertTrue(
                mapped.border.control.contrastRatio(mapped.surface.base) >= HbContrast.TextMinimum,
                "Boundary contrast is below the text minimum",
            )
        }
    }

    @Test
    fun disabledRoleStaysDistinct() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { base ->
            val mapped = HbContrastSchemes.highContrast(base)
            assertTrue(
                mapped.text.disabled != mapped.text.secondary,
                "Disabled text must stay distinguishable from secondary text",
            )
        }
    }

    @Test
    fun validatedPairsStayUnchanged() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { base ->
            val mapped = HbContrastSchemes.highContrast(base)
            assertEquals(base.action, mapped.action)
            assertEquals(base.status, mapped.status)
        }
    }

    @Test
    fun selectionIsStrengthenedInsteadOfTinted() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { base ->
            val mapped = HbContrastSchemes.highContrast(base)
            assertTrue(
                mapped.selection.background.contrastRatio(mapped.surface.base) >=
                    HbContrast.NonTextMinimum,
                "Selected container must be distinguishable from the adjacent surface",
            )
            assertTrue(
                mapped.selection.foreground.contrastRatio(mapped.selection.background) >=
                    HbContrast.TextMinimum,
                "Selected content must reach the text minimum on its container",
            )
        }
    }

    @Test
    fun secondaryTextReachesTextMinimumOnSurfaces() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { base ->
            val mapped = HbContrastSchemes.highContrast(base)
            listOf(mapped.surface.base, mapped.surface.subtle, mapped.surface.raised).forEach { surface ->
                assertTrue(
                    mapped.text.secondary.contrastRatio(surface) >= HbContrast.TextMinimum,
                    "Secondary text contrast is below the text minimum",
                )
            }
        }
    }
}
