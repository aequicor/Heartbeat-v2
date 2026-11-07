import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Assert
import org.junit.Test

/**
 * Konsist architecture tests for the feature-first structure from
 * `docs/specs/architecture.md`.
 *
 * They read production sources of every feature and Design System module.
 * Module-level dependency direction and framework bans stay in the
 * `heartbeat-architecture-check` Gradle gate; these tests add the source-level
 * checks that Gradle cannot express: the naming standard, contract/implementation
 * matching and intra-module layer direction.
 */
class ArchitectureTest {

    private val rootPackage = "io.github.aequicor.heartbeat"

    private val featurePrefix = "$rootPackage.features"

    /** Production sources of all feature modules; the test runs with workingDir = repo root. */
    private val featureScope = Konsist.scopeFromProduction(moduleName = ":features..").also {
        require(it.files.isNotEmpty()) { "No production Kotlin files found in features" }
    }

    /** Production sources of the Design System modules. */
    private val designSystemScope = Konsist.scopeFromProduction(moduleName = ":designsystem..").also {
        require(it.files.isNotEmpty()) { "No production Kotlin files found in the designsystem" }
    }

    /** Matches `...features.<any feature>.<role>` and its subpackages. */
    private fun rolePattern(role: String): String = "$featurePrefix..$role.."

    @Test
    fun declarationsFollowNamingStandard() {
        Assert.assertEquals(listOf<String>(), namingViolations(featureScope.files))
    }

    @Test
    fun designSystemApiUsesHbPrefix() {
        Assert.assertEquals(listOf<String>(), namingViolations(designSystemScope.files))
    }

    @Test
    fun apiContractsArePublic() {
        val violations = featureScope.files
            .filter { file -> file.packagee?.name?.endsWith(".api") == true }
            .flatMap { file ->
                file.declarations(includeNested = false, includeLocal = false)
                    .filter { it.kind() != DeclarationKind.OTHER }
                    .filterNot { it.isExplicitlyPublic() }
                    .map { "${file.projectPath}: api contract must be public: ${it.nameOrNull()}" }
            }

        Assert.assertEquals(listOf<String>(), violations)
    }

    @Test
    fun implementationDetailsAreInternal() {
        Assert.assertEquals(listOf<String>(), visibilityViolations(featureScope.files))
    }

    @Test
    fun nestedActionAndEffectMembersAreNotContracts() {
        val actionFile = featureScope.files.single { it.projectPath.endsWith("EditorAction.kt") }
        val apiPackage = "$featurePrefix.editor.api"
        val nested = actionFile.classes(includeNested = true, includeLocal = false)
            .map { it.name }
            .filter { it != "EditorAction" }

        Assert.assertTrue("Nested action members are parsed: $nested", "TitleChanged" in nested)
        nested.forEach { name ->
            Assert.assertNotNull(
                "Nested $name must not pass the api contract rule",
                NamingRules.violation(apiPackage, DeclarationKind.CLASS, name),
            )
        }
        Assert.assertEquals(listOf<String>(), namingViolations(listOf(actionFile)))
    }

    @Test
    fun defaultImplementationImplementsSameNamedContract() {
        featureScope.classesAndObjects(includeNested = false, includeLocal = false)
            .filter { it.name.startsWith("Default") }
            .assertTrue { implementation ->
                implementation.parentInterfaces(indirectParents = true).any { parent ->
                    parent.sourceDeclaration
                        ?.asInterfaceDeclaration()
                        ?.name == implementation.name.removePrefix("Default")
                }
            }
    }

    @Test
    fun featureLayersFollowImportDirection() {
        featureScope.assertArchitecture {
            val api = Layer("API", rolePattern("api"))
            val domain = Layer("Domain", rolePattern("domain"))
            val data = Layer("Data", rolePattern("data"))
            val presentation = Layer("Presentation", rolePattern("presentation"))
            val di = Layer("DI", rolePattern("di"))
            val component = Layer("Component", rolePattern("component"))
            val ui = Layer("UI", rolePattern("ui"))

            api.dependsOnNothing()
            domain.dependsOnNothing()
            data.dependsOn(domain)
            presentation.dependsOn(domain, api)
            di.dependsOn(api, domain, presentation)
            component.dependsOn(api)
            ui.dependsOn(api)
        }
    }

    private fun namingViolations(files: List<KoFileDeclaration>): List<String> =
        declarationViolations(files) { declaration ->
            NamingRules.violation(
                packageName = declaration.packageName,
                declarationKind = declaration.kind,
                declarationName = declaration.name,
                isPublic = declaration.isPublic,
                annotations = declaration.annotations,
            )
        }

    private fun visibilityViolations(files: List<KoFileDeclaration>): List<String> =
        declarationViolations(files) { declaration ->
            VisibilityRules.violation(
                packageName = declaration.packageName,
                declarationName = declaration.name,
                isPublic = declaration.isPublic,
                annotations = declaration.annotations,
            )
        }

    private fun declarationViolations(
        files: List<KoFileDeclaration>,
        rule: (DeclarationInfo) -> String?,
    ): List<String> = files.flatMap { file ->
        val packageName = file.packagee?.name.orEmpty()
        file.declarations(includeNested = false, includeLocal = false).mapNotNull { declaration ->
            // Imports and the package directive are declarations for Konsist too.
            val kind = declaration.kind()
            if (kind == DeclarationKind.OTHER) return@mapNotNull null
            val name = declaration.nameOrNull() ?: return@mapNotNull null
            rule(
                DeclarationInfo(
                    packageName = packageName,
                    kind = kind,
                    name = name,
                    isPublic = declaration.isPublicOrDefault(),
                    annotations = declaration.annotationNames(),
                ),
            )?.let { "${file.projectPath}: $it" }
        }
    }

    private data class DeclarationInfo(
        val packageName: String,
        val kind: DeclarationKind,
        val name: String,
        val isPublic: Boolean,
        val annotations: List<String>,
    )

    private fun KoBaseDeclaration.kind(): DeclarationKind = when (this) {
        is KoInterfaceDeclaration -> DeclarationKind.INTERFACE
        is KoObjectDeclaration -> DeclarationKind.OBJECT
        is KoClassDeclaration -> DeclarationKind.CLASS
        is KoFunctionDeclaration -> DeclarationKind.FUNCTION
        else -> DeclarationKind.OTHER
    }

    private fun KoBaseDeclaration.nameOrNull(): String? = (this as? KoNameProvider)?.name

    private fun KoBaseDeclaration.annotationNames(): List<String> = when (this) {
        is KoAnnotationProvider -> {
            // Metro `@Inject` often sits on the constructor (`class X @Inject constructor()`),
            // so constructor annotations are part of the DI boundary as well.
            val constructorAnnotations = (this as? KoClassDeclaration)
                ?.primaryConstructor
                ?.annotations
                ?.map { it.name }
                .orEmpty()
            annotations.map { it.name } + constructorAnnotations
        }

        else -> listOf()
    }

    private fun KoBaseDeclaration.isPublicOrDefault(): Boolean = when (this) {
        is KoVisibilityModifierProvider ->
            !hasInternalModifier && !hasPrivateModifier && !hasProtectedModifier

        else -> true
    }

    private fun KoBaseDeclaration.isExplicitlyPublic(): Boolean =
        (this as? KoVisibilityModifierProvider)?.hasPublicModifier == true
}
