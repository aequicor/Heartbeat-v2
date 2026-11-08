import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import java.io.File

/**
 * Token rule from `docs/specs/design-system.md` ("Токены") and HB-DS-040: UI feature sources use
 * semantic roles or component APIs, so a raw `Color`, `Dp`, `TextUnit` or `dp`/`sp` literal is a
 * violation instead of a silent second design system.
 *
 * The rule tokenizes Kotlin with the compiler lexer and checks only code tokens, so a color or size
 * mentioned in a comment, a character literal or a string literal is not a violation, and a
 * constructor call is recognized together with its named arguments. The design system layer,
 * generated code and platform renderer adapters are exempt by an anchored module path; a feature
 * cannot become exempt by putting a component in a subdirectory whose name happens to contain
 * `theme`, `renderer` or `adapter`.
 */
internal object TokenUsageRules {

    private val repoRoot = File(System.getProperty("user.dir")).absoluteFile.path.replace('\\', '/')

    private val exemptions = listOf(
        Regex("""^designsystem/"""),
        Regex("""(?:^|/)build/generated/"""),
        Regex("""^platform/(?:[^/]+/)?(?:renderer|adapter)/"""),
    )

    private val forbidden = listOf(
        "raw Color constructor" to Regex(
            """\bColor\s*\(\s*(?:(?:color|red)\s*=\s*)?(?:""" +
                """0x[0-9a-fA-F_]+(?:\s*\)|\s*,)|""" +
                """\d[\d_]*(?:\.\d+)?[fF]?\s*,)""",
        ),
        "raw palette constant" to Regex(
            """\bColor\s*\.\s*(Red|Green|Blue|Black|White|Gray|LightGray|DarkGray|Yellow|Cyan|Magenta)\b""",
        ),
        "raw Dp constructor" to Regex("""\bDp\s*\(\s*-?\d[\d_]*(?:\.\d+)?[fF]?\s*\)"""),
        "raw TextUnit constructor" to Regex("""\bTextUnit\s*\(\s*-?\d[\d_]*(?:\.\d+)?[fF]?\s*,"""),
        "literal dp" to Regex("""(?<![\w.])\d[\d_]*(?:\.\d+)?\.dp\b"""),
        "literal sp" to Regex("""(?<![\w.])\d[\d_]*(?:\.\d+)?\.sp\b"""),
    )

    fun isExempt(projectPath: String): Boolean {
        val normalized = relativePath(projectPath)
        return exemptions.any { it.containsMatchIn(normalized) }
    }

    fun violations(projectPath: String, source: String): List<String> {
        if (isExempt(projectPath)) return listOf()
        val normalized = relativePath(projectPath)
        val code = codeOnly(source)
        return forbidden.flatMap { (reason, pattern) ->
            pattern.findAll(code).map { match -> "$normalized: $reason: ${match.value}" }.toList()
        }
    }

    /** Keeps code tokens and replaces comments, whitespace and literals with blanks. */
    private fun codeOnly(source: String): String {
        val lexer = KotlinLexer()
        lexer.start(source)
        val code = StringBuilder(source.length)
        while (true) {
            val type = lexer.tokenType ?: break
            appendToken(source, lexer.tokenStart, lexer.tokenEnd, type, code)
            lexer.advance()
        }
        return code.toString()
    }

    private fun appendToken(
        source: String,
        start: Int,
        end: Int,
        type: IElementType,
        code: StringBuilder,
    ) {
        if (isIgnored(type)) {
            for (index in start until end) {
                code.append(if (source[index] == '\n') '\n' else ' ')
            }
        } else {
            code.append(source, start, end)
        }
    }

    private fun isIgnored(type: IElementType): Boolean =
        type == KtTokens.WHITE_SPACE ||
            KtTokens.COMMENTS.contains(type) ||
            KtTokens.STRINGS.contains(type) ||
            type == KtTokens.CHARACTER_LITERAL

    private fun relativePath(projectPath: String): String {
        val unix = projectPath.replace('\\', '/')
        val rootWithSlash = "$repoRoot/"
        return when {
            unix.startsWith(rootWithSlash, ignoreCase = true) -> unix.substring(rootWithSlash.length)
            unix.startsWith("/") -> unix.trimStart('/')
            else -> unix
        }
    }
}
