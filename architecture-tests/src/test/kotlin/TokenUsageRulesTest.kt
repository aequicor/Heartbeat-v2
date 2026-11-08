import org.junit.Assert
import org.junit.Test

/**
 * Unit tests of the token usage rule (HB-DS-040): a UI feature may not introduce a raw color or a
 * literal size, while the design system layer, generated code and platform renderer adapters stay
 * exempt. The rule parses Kotlin, so comments and string literals must not create violations.
 */
class TokenUsageRulesTest {

    @Test
    fun rawColorLiteralIsAViolation() {
        Assert.assertEquals(
            listOf("features/editor/ui/src/Editor.kt: raw Color constructor: Color(0xFF7B6D9A)"),
            TokenUsageRules.violations(
                "features/editor/ui/src/Editor.kt",
                "val c = Color(0xFF7B6D9A)",
            ),
        )
    }

    @Test
    fun namedColorArgumentsAreViolations() {
        val violations = TokenUsageRules.violations(
            "features/editor/ui/src/Editor.kt",
            "val c = Color(red = 255, green = 0, blue = 0)",
        )

        Assert.assertEquals(1, violations.size)
    }

    @Test
    fun paletteConstantIsAViolation() {
        Assert.assertTrue(
            TokenUsageRules.violations(
                "features/editor/ui/src/Editor.kt",
                "val c = Color.Red",
            ).isNotEmpty(),
        )
    }

    @Test
    fun literalSizesAreViolations() {
        val violations = TokenUsageRules.violations(
            "features/editor/ui/src/Editor.kt",
            "fun sample() { Modifier.padding(8.dp).width(12.5.dp).fontSize(13.sp) }",
        )

        Assert.assertEquals(3, violations.size)
    }

    @Test
    fun equivalentSizeConstructorsAreViolations() {
        val violations = TokenUsageRules.violations(
            "features/editor/ui/src/Editor.kt",
            "fun sample() { val a = Dp(8f); val b = TextUnit(13f, TextUnitType.Sp) }",
        )

        Assert.assertEquals(2, violations.size)
    }

    @Test
    fun commentsAndStringLiteralsAreAccepted() {
        val source = """
            // Color(0xFF000000) and 8.dp in a comment
            /* Color.Red and 12.sp in a block comment */
            val message = "Color.Red 8.dp Dp(8f)"
        """.trimIndent()

        Assert.assertEquals(
            listOf<String>(),
            TokenUsageRules.violations("features/editor/ui/src/Editor.kt", source),
        )
    }

    @Test
    fun tokenLookupsAreAccepted() {
        val source = """
            fun sample() {
                Modifier.padding(HbSpacing.S).height(HbSizes.controlHeight)
                val style = HbTypography.Body
                val c = HbTheme.colors.text.primary
            }
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

    @Test
    fun featureSubdirectoriesCannotBorrowTheExemptNames() {
        val source = "val c = Color(0xFF000000); val d = 8.dp"

        listOf("theme", "renderer", "adapter").forEach { directory ->
            Assert.assertTrue(
                "$directory must not exempt a feature component",
                TokenUsageRules.violations(
                    "features/editor/ui/src/$directory/Editor.kt",
                    source,
                ).isNotEmpty(),
            )
        }
    }

    @Test
    fun platformRendererAdaptersAreExempt() {
        val source = "val c = Color(0xFF000000); val d = 8.dp"

        Assert.assertEquals(
            listOf<String>(),
            TokenUsageRules.violations("platform/desktop/renderer/DesktopColors.kt", source),
        )
    }
}
