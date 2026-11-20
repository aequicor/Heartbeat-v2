package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastSchemes
import kotlin.test.Test
import kotlin.test.assertEquals

class HbColorMappingTest {
    @Test
    fun allReadableTextRolesSurviveAppearanceAndContrastMapping() {
        listOf(HbColorPalettes.Light, HbColorPalettes.Dark).forEach { base ->
            listOf(base, HbContrastSchemes.highContrast(base)).forEach { palette ->
                val resolved = palette.toHbColors().text
                val tokens = with(palette.text) { listOf(primary, secondary, muted, disabled) }
                val colors = with(resolved) { listOf(primary, secondary, muted, disabled) }
                tokens.zip(colors).forEach { (token, color) ->
                    assertEquals(token.toArgb(), color.toArgb().toLong() and 0xFFFFFFFFL)
                }
            }
        }
    }

    @Test
    fun auroraUsesTheSharedLightTextHierarchy() {
        val text = HbColorPalettes.Light.toHbColors().text
        listOf(HbAuroraColors.Ink, HbAuroraColors.DesktopInk, HbAuroraColors.MessageInk,
            HbAuroraColors.WorkInk, HbAuroraColors.NavigationInk).forEach { assertEquals(text.primary, it) }
        listOf(HbAuroraColors.Muted, HbAuroraColors.WorkMuted, HbAuroraColors.NavigationMuted)
            .forEach { assertEquals(text.secondary, it) }
        listOf(HbAuroraColors.MessageMuted, HbAuroraColors.DesktopWorkMuted)
            .forEach { assertEquals(text.muted, it) }
    }
}
