@file:OptIn(
    org.jetbrains.kotlin.CoreEnvironmentDeprecation::class,
    org.jetbrains.kotlin.config.CompilerConfiguration.Internals::class,
    org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class,
)

import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File

/**
 * Token rule from `design/design-system.md` ("Токены") and HB-DS-040: UI feature sources use
 * semantic roles or component APIs, so a raw `Color`, `Dp`, `TextUnit` or `dp`/`sp` literal is a
 * violation instead of a silent second design system.
 *
 * The rule parses Kotlin syntax with the Kotlin PSI instead of matching raw source text, so a
 * color or size mentioned in a comment or a string literal is not a violation, and a constructor
 * call is recognized together with its named arguments. The design system layer, generated code
 * and platform renderer adapters are exempt by an anchored module path; a feature cannot become
 * exempt by putting a component in a subdirectory whose name happens to contain `theme`, `renderer`
 * or `adapter`.
 */
internal object TokenUsageRules {

    private val repoRoot = File(System.getProperty("user.dir")).absoluteFile.path.replace('\\', '/')

    private val project by lazy {
        val configuration = CompilerConfiguration().apply {
            extensionsStorage = CompilerPluginRegistrar.ExtensionStorage()
        }
        KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable("token-usage-rules"),
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        ).project
    }

    private val fileFactory by lazy { PsiFileFactory.getInstance(project) }

    private val exemptions = listOf(
        Regex("""^designsystem/"""),
        Regex("""(?:^|/)build/generated/"""),
        Regex("""^platform/(?:[^/]+/)?(?:renderer|adapter)/"""),
    )

    private val paletteConstants = setOf(
        "Red",
        "Green",
        "Blue",
        "Black",
        "White",
        "Gray",
        "LightGray",
        "DarkGray",
        "Yellow",
        "Cyan",
        "Magenta",
    )

    fun isExempt(projectPath: String): Boolean {
        val normalized = relativePath(projectPath)
        return exemptions.any { it.containsMatchIn(normalized) }
    }

    fun violations(projectPath: String, source: String): List<String> {
        if (isExempt(projectPath)) return listOf()
        val normalized = relativePath(projectPath)
        val violations = mutableListOf<String>()
        parse(source).accept(
            object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    val callee = expression.calleeExpression?.text
                    val hasNumericArgument = expression.valueArguments.any { argument ->
                        argument.getArgumentExpression()?.text?.let(::isNumericLiteral) == true
                    }
                    when {
                        callee == "Color" && hasNumericArgument -> {
                            violations += "$normalized: raw Color constructor: ${expression.text}"
                        }

                        (callee == "Dp" || callee == "TextUnit") && hasNumericArgument -> {
                            violations += "$normalized: raw $callee constructor: ${expression.text}"
                        }
                    }
                    super.visitCallExpression(expression)
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    val receiver = expression.receiverExpression.text
                    val selector = expression.selectorExpression?.text.orEmpty()
                    when {
                        isNumericLiteral(receiver) && selector == "dp" -> {
                            violations += "$normalized: literal dp: ${expression.text}"
                        }

                        isNumericLiteral(receiver) && selector == "sp" -> {
                            violations += "$normalized: literal sp: ${expression.text}"
                        }

                        receiver.substringAfterLast('.') == "Color" && selector in paletteConstants -> {
                            violations += "$normalized: raw palette constant: ${expression.text}"
                        }
                    }
                    super.visitDotQualifiedExpression(expression)
                }
            },
        )
        return violations
    }

    private fun parse(source: String): KtFile =
        fileFactory.createFileFromText("TokenUsage.kt", KotlinLanguage.INSTANCE, source) as KtFile

    private fun relativePath(projectPath: String): String {
        val unix = projectPath.replace('\\', '/')
        val rootWithSlash = "$repoRoot/"
        return when {
            unix.startsWith(rootWithSlash, ignoreCase = true) -> unix.substring(rootWithSlash.length)
            unix.startsWith("/") -> unix.trimStart('/')
            else -> unix
        }
    }

    private fun isNumericLiteral(text: String): Boolean =
        NumericLiteral.matches(text.replace("_", ""))

    private val NumericLiteral = Regex(
        """^-?(?:0x[0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)[fF]?$""",
    )
}
