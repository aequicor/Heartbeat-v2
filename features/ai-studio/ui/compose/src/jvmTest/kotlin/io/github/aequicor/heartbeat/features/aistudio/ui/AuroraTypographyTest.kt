@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkRole
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkText
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraMessageStyle
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraTimestampStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuroraTypographyTest {
    @Test
    fun readingWorkActionsAndTimeKeepDistinctRolesAtRegularWeightAndEveryTextScale() {
        for (touch in listOf(false, true)) for (scale in listOf(1f, 1.25f, 2f)) {
            val scene = ImageComposeScene(800, 700) {
                AuroraTestTheme(textScale = scale, density = if (touch) HbDensity.Touch else HbDensity.Dense) {
                    Column {
                        BasicText("Ответ\nСледующая строка", style = auroraMessageStyle())
                        AuroraWorkText("Подробности инструмента", AuroraWorkRole.Detail)
                        AuroraWorkText("Открыть результат", AuroraWorkRole.Action)
                        AuroraWorkText("Готово", AuroraWorkRole.Metadata)
                        BasicText("14:06", style = auroraTimestampStyle())
                        BasicText("val result = session.id", style = HbTypography.Code)
                    }
                }
            }
            try {
                scene.render().close()
                val root = scene.semanticsOwners.single().rootSemanticsNode
                val reading = root.layout("Ответ\nСледующая строка")
                val detail = root.layout("Подробности инструмента")
                val action = root.layout("Открыть результат")
                val status = root.layout("Готово")
                val metadata = root.layout("14:06")
                val code = root.layout("val result = session.id")
                assertRole(reading, if (touch) 16 else 15, if (touch) 24 else 22, scale)
                assertRole(detail, if (touch) 16 else 14, if (touch) 24 else 20, scale)
                assertRole(action, 14, 20, scale)
                assertRole(metadata, if (touch) 13 else 12, if (touch) 18 else 16, scale)
                assertRole(status, if (touch) 13 else 12, if (touch) 18 else 16, scale)
                assertRole(code, 13, 20, scale)
                for (layout in listOf(reading, detail, action, metadata, status)) {
                    assertEquals(AuroraTestFonts.ui, layout.layoutInput.style.fontFamily)
                }
                assertEquals(AuroraTestFonts.code, code.layoutInput.style.fontFamily)
                assertTrue(metadata.layoutInput.style.fontSize.value < reading.layoutInput.style.fontSize.value)
                assertEquals("tnum", metadata.layoutInput.style.fontFeatureSettings)
                assertEquals(
                    (if (touch) 24f else 22f) * scale,
                    reading.getLineBaseline(1) - reading.getLineBaseline(0), 1f,
                )
            } finally {
                scene.close()
            }
        }
    }

    private fun assertRole(
        layout: TextLayoutResult,
        size: Int,
        lineHeight: Int,
        scale: Float,
        weight: FontWeight = FontWeight.Normal,
    ) {
        val style = layout.layoutInput.style
        assertEquals((size * scale).sp, style.fontSize)
        assertEquals((lineHeight * scale).sp, style.lineHeight)
        assertEquals(weight, style.fontWeight)
        assertEquals(0.sp, style.letterSpacing)
        assertEquals(Shadow.None, style.shadow, "Typography never inherits the previous contrast shadow")
        assertTrue(!layout.didOverflowHeight, "Every line fits the allocated height")
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertTrue(!layout.isLineEllipsized(line), "Typography keeps complete lines")
            assertTrue(layout.getLineLeft(line) >= -1f)
            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f,
                "Visible text fits the occupied width")
            assertTrue(layout.getLineBaseline(line) in 0f..layout.size.height.toFloat())
        }
    }

    private fun SemanticsNode.layout(text: String): TextLayoutResult {
        val node = checkNotNull(findText(text))
        val results = mutableListOf<TextLayoutResult>()
        check(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) == true)
        return results.single()
    }

    private fun SemanticsNode.findText(text: String): SemanticsNode? =
        if (config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text == text) this
        else children.firstNotNullOfOrNull { it.findText(text) }
}
