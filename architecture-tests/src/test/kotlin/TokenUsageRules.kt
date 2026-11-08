/**
 * Token rule from `docs/specs/design-system.md` ("Токены") and HB-DS-040: UI feature sources use
 * semantic roles or component APIs, so a raw `Color`, `dp` or `sp` literal is a violation instead
 * of a silent second design system.
 *
 * The rule is pure so [TokenUsageRulesTest] covers the accepted and rejected forms directly; the
 * Konsist test in [ArchitectureTest] feeds the production sources of the feature UI modules.
 * Implementation modules of the design system itself, generated code and documented renderer
 * adapters are exempt, because they are the layer that resolves tokens.
 */
internal object TokenUsageRules {

    private val exemptions = listOf(
        "/designsystem/",
        "/generated/",
        "/theme/",
        "/renderer/",
        "/adapter/",
    )

    private val forbidden = listOf(
        "raw Color constructor" to
            Regex("""\bColor\s*\(\s*(?:0x[0-9a-fA-F]+|\d{1,3}\s*,)[^)]*\)"""),
        "raw palette constant" to
            Regex(
                """\bColor\s*\.\s*(Red|Green|Blue|Black|White|Gray|LightGray|DarkGray|Yellow|Cyan|Magenta)\b""",
            ),
        "literal dp" to Regex("""(?<![\w.])\d+(?:\.\d+)?\.dp\b"""),
        "literal sp" to Regex("""(?<![\w.])\d+(?:\.\d+)?\.sp\b"""),
    )

    fun isExempt(projectPath: String): Boolean {
        val normalized = projectPath.replace('\\', '/')
        return exemptions.any(normalized::contains)
    }

    fun violations(projectPath: String, source: String): List<String> {
        if (isExempt(projectPath)) return listOf()
        val normalized = projectPath.replace('\\', '/')
        return forbidden.flatMap { (reason, pattern) ->
            pattern.findAll(source).map { match -> "$normalized: $reason: ${match.value}" }.toList()
        }
    }
}
