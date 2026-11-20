package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.foundation.HbTypeScales
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Text scale checks (HB-DS-007): the neutral scale keeps the documented desktop sizes and a larger
 * user preference scales every role, including the line height, so text containers grow.
 */
class HbTypographyScaleTest {

    @Test
    fun neutralScaleKeepsDocumentedDesktopValues() {
        val styles = defaultTypeStyles(defaultTypeScale())

        assertEquals(
            listOf(
                22f to 28f, 18f to 24f, 15f to 20f, 15f to 22f, 14f to 20f,
                14f to 20f, 13f to 18f, 13f to 20f, 12f to 16f,
            ),
            styles.roles().map { it.fontSize.value to it.lineHeight.value },
        )
    }

    @Test
    fun touchKeepsItsReadingAndMetadataMetrics() {
        val styles = HbTypeScales.Touch.toStyles(textScale = 1f)
        assertEquals(
            listOf(
                22f to 28f, 18f to 24f, 15f to 22f, 16f to 24f, 16f to 24f,
                14f to 20f, 14f to 20f, 13f to 20f, 13f to 18f,
            ),
            styles.roles().map { it.fontSize.value to it.lineHeight.value },
        )
    }

    @Test
    fun fractionalAndDoubleScalePreserveEveryRoleAndItsHierarchy() {
        for (scale in listOf(HbTypeScales.Desktop, HbTypeScales.Touch)) {
            val neutral = scale.toStyles(textScale = 1f)
            for (factor in listOf(1.25f, 2f)) {
                val scaled = scale.toStyles(textScale = factor)
                neutral.roles().zip(scaled.roles()).forEach { (original, enlarged) ->
                    assertEquals(original.fontSize.value * factor, enlarged.fontSize.value, 0.01f)
                    assertEquals(original.lineHeight.value * factor, enlarged.lineHeight.value, 0.01f)
                    assertEquals(original.fontWeight, enlarged.fontWeight)
                }
            }
        }
    }

    @Test
    fun everyRoleUsesRegularWeightWithoutFontSynthesis() {
        for (scale in listOf(HbTypeScales.Desktop, HbTypeScales.Touch)) {
            scale.toStyles(textScale = 1f).roles().forEach { style ->
                assertEquals(FontWeight.Normal, style.fontWeight)
                assertEquals(FontSynthesis.None, style.fontSynthesis)
            }
        }
    }

    @Test
    fun everyRolePreservesPlatformSettingsAndTheAssignedFontFamily() {
        val platformStyle = PlatformTextStyle(spanStyle = null, paragraphStyle = PlatformParagraphStyle())
        val styles = HbTypeScales.Desktop.toStyles(
            textScale = 1f, fontFamily = FontFamily.SansSerif,
            platformStyle = platformStyle, codeFontFamily = FontFamily.Serif,
        )
        styles.roles().forEach { style ->
            assertEquals(platformStyle, style.platformStyle)
            assertEquals(FontSynthesis.None, style.fontSynthesis)
            assertEquals(0.sp, style.letterSpacing)
            assertEquals(Shadow.None, style.shadow)
            assertEquals(
                LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                style.lineHeightStyle,
            )
            assertEquals(if (style === styles.code) FontFamily.Serif else FontFamily.SansSerif, style.fontFamily)
        }
        assertEquals("tnum", styles.metadata.fontFeatureSettings)
        assertEquals(FontFamily.Monospace, HbTypeScales.Desktop.toStyles(1f).code.fontFamily)
    }

    private fun HbTypeStyles.roles(): List<TextStyle> =
        listOf(pageTitle, sectionTitle, paneTitle, readingBody, detail, uiLabel, caption, code, metadata)
}
