import org.junit.Assert
import org.junit.Test

/**
 * Unit tests of the token usage rule (HB-DS-040): a UI feature may not introduce a raw color or a
 * literal size, while the design system layer and generated code stay exempt.
 */
class TokenUsageRulesTest {

    @Test
    fun rawColorLiteralIsAViolation() {
        Assert.assertEquals(
            listOf("features/editor/ui/src/Editor.kt: raw Color constructor: Color(0xFF7B6D9A)"),
            TokenUsageRules.violations("features/editor/ui/src/Editor.kt", "val c = Color(0xFF7B6D9A)"),
        )
    }

    @Test
    fun paletteConstantIsAViolation() {
        Assert.assertTrue(
            TokenUsageRules.violations("features/editor/ui/src/Editor.kt", "val c = Color.Red").isNotEmpty(),
        )
    }

    @Test
    fun literalSizesAreViolations() {
        val violations = TokenUsageRules.violations(
            "features/editor/ui/src/Editor.kt",
            "Modifier.padding(8.dp).width(12.5.dp).fontSize(13.sp)",
        )

        Assert.assertEquals(3, violations.size)
    }

    @Test
    fun tokenLookupsAreAccepted() {
        val source = """
            Modifier.padding(HbSpacing.S).height(HbSizes.controlHeight)
            val style = HbTypography.Body
            val c = HbTheme.colors.text.primary
        """.trimIndent()

        Assert.assertEquals(
            listOf<String>(),
            TokenUsageRules.violations("features/editor/ui/src/Editor.kt", source),
        )
    }

    @Test
    fun designSystemAndGeneratedSourcesAreExempt() {
        val source = "val c = Color(0xFF000000); val d = 8.dp"

        Assert.assertEquals(
            listOf<String>(),
            TokenUsageRules.violations("designsystem/compose/theme/src/X.kt", source),
        )
        Assert.assertEquals(
            listOf<String>(),
            TokenUsageRules.violations("features/editor/ui/build/generated/Res.kt", source),
        )
    }
}
