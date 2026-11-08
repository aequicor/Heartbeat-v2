package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reference layer and translucent-color contract (HB-DS-002, HB-DS-004). */
class HbReferenceColorsTest {

    @Test
    fun everyReferenceTokenIsOpaque() {
        val tokens = listOf(
            HbReferenceColors.White,
            HbReferenceColors.Paper,
            HbReferenceColors.Mist,
            HbReferenceColors.Cloud,
            HbReferenceColors.Fog,
            HbReferenceColors.Ink,
            HbReferenceColors.InkSoft,
            HbReferenceColors.InkMuted,
            HbReferenceColors.Steel,
            HbReferenceColors.Charcoal,
            HbReferenceColors.Graphite,
            HbReferenceColors.Slate,
            HbReferenceColors.Basalt,
            HbReferenceColors.Coal,
            HbReferenceColors.Snow,
            HbReferenceColors.SnowSoft,
            HbReferenceColors.SnowMuted,
            HbReferenceColors.SlateLine,
            HbReferenceColors.SteelLine,
            HbReferenceColors.Violet,
            HbReferenceColors.VioletInk,
            HbReferenceColors.Lilac,
            HbReferenceColors.LilacInk,
            HbReferenceColors.Plum,
            HbReferenceColors.GraphiteAction,
            HbReferenceColors.GraphiteActionHover,
            HbReferenceColors.GraphiteActionPressed,
            HbReferenceColors.Pearl,
            HbReferenceColors.PearlHover,
            HbReferenceColors.PearlPressed,
            HbReferenceColors.Green,
            HbReferenceColors.GreenWash,
            HbReferenceColors.Amber,
            HbReferenceColors.AmberWash,
            HbReferenceColors.Red,
            HbReferenceColors.Blue,
            HbReferenceColors.BlueWash,
            HbReferenceColors.GreenLight,
            HbReferenceColors.GreenLightWash,
            HbReferenceColors.AmberLight,
            HbReferenceColors.AmberLightWash,
            HbReferenceColors.RedLight,
            HbReferenceColors.RedLightWash,
            HbReferenceColors.BlueLight,
            HbReferenceColors.BlueLightWash,
        )

        tokens.forEach { token ->
            assertEquals(255, token.alpha, "Reference token must be opaque: $token")
        }
    }

    @Test
    fun semanticRolesResolveFromTheReferenceLayer() {
        assertEquals(HbReferenceColors.Ink, HbColorPalettes.Light.text.primary)
        assertEquals(HbReferenceColors.VioletInk, HbColorPalettes.Light.focus)
        assertEquals(HbReferenceColors.Pearl, HbColorPalettes.Dark.action.primaryBackground)
        assertEquals(HbReferenceColors.LilacInk, HbColorPalettes.Dark.focus)
    }

    @Test
    fun contrastRejectsTranslucentColors() {
        val translucent = HbReferenceColors.White.copy(alpha = 128)

        assertFailsWith<IllegalArgumentException> {
            translucent.contrastRatio(HbReferenceColors.Ink)
        }
    }

    @Test
    fun translucentColorCompositesOverTheBackground() {
        val translucent = HbReferenceColors.Ink.copy(alpha = 128)

        val composited = translucent.compositedOver(HbReferenceColors.White)

        assertEquals(255, composited.alpha)
        assertTrue(composited.red > HbReferenceColors.Ink.red, "Compositing over white must lighten the color")
        assertTrue(composited.contrastRatio(HbReferenceColors.White) < 21.0)
    }
}
