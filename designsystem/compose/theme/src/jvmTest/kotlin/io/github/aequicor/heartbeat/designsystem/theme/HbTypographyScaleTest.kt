package io.github.aequicor.heartbeat.designsystem.theme

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

        assertEquals(15f, styles.readingBody.fontSize.value, 0.01f)
        assertEquals(24f, styles.readingBody.lineHeight.value, 0.01f)
        assertEquals(24f, styles.pageTitle.fontSize.value, 0.01f)
        assertEquals(32f, styles.pageTitle.lineHeight.value, 0.01f)
    }

    @Test
    fun doubleScaleDoublesEveryRole() {
        val styles = defaultTypeScale().toStyles(textScale = 2f)

        assertEquals(30f, styles.readingBody.fontSize.value, 0.01f)
        assertEquals(48f, styles.readingBody.lineHeight.value, 0.01f)
        assertEquals(26f, styles.uiLabel.fontSize.value, 0.01f)
        assertEquals(36f, styles.uiLabel.lineHeight.value, 0.01f)
    }
}
