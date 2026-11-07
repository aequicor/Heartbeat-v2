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
import com.lemonappdev.konsist.api.provider.modifier.KoModifierProvider
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

    /** Framework groups that would make the portable token contract renderer-specific. */
    private val composeImportPrefixes = listOf("androidx.compose", "org.jetbrains.compose")

    private val featurePrefix = "$rootPackage.features"

    /** Production sources of all feature modules; the test runs with workingDir = repo root. */
    private val featureScope = Konsist.scopeFromProduction(moduleName = ":features..").also {
        require(it.files.isNotEmpty()) { "No production Kotlin files found in features" }
    }

    /** Production sources of the Design System modules. */
    private val designSystemScope = Konsist.scopeFromProduction(moduleName = ":designsystem..").also {
        require(it.files.isNotEmpty()) { "No production Kotlin files found in the designsystem" }
    }

    /** Production sources of the infrastructure modules; `shell` may stay empty. */
    private val infrastructureScope = Konsist.scopeFromProduction(moduleName = ":core..")

    private val diScope = Konsist.scopeFromProduction(moduleName = ":di..")

    private val shellScope = Konsist.scopeFromProduction(moduleName = ":shell..")

    private val platformScope = Konsist.scopeFromProduction(moduleName = ":platform..")

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

    /**
     * HB-ARCH-045: the portable token contract must stay renderer-neutral, so the foundation
     * module cannot import Compose. The module classpath is checked by
     * `heartbeat-architecture-check`; this test catches the import that would pull Compose into
     * the contract even if the dependency is declared under another name.
     */
    @Test
    fun foundationStaysRendererNeutral() {
        val foundationFiles = designSystemScope.files.filter { file ->
            file.projectPath.replace('\\', '/').contains("/designsystem/foundation/")
        }
        Assert.assertTrue("No foundation sources found: $foundationFiles", foundationFiles.isNotEmpty())

        val violations = foundationFiles.flatMap { file ->
            file.imports
                .map { it.name }
                .filter { name -> composeImportPrefixes.any(name::startsWith) }
                .map { name -> "${file.projectPath}: $name" }
        }

        Assert.assertEquals(listOf<String>(), violations)
    }

    @Test
    fun apiContractsArePublic() {
        val violations = featureScope.files
            .map { file -> file to file.packagee?.name.orEmpty() }
            .filter { (_, packageName) -> NamingRules.isApiPackage(packageName) }
            .flatMap { (file, packageName) ->
                file.declarations(includeNested = false, includeLocal = false)
                    .filter { it.kind() != DeclarationKind.OTHER }
                    .mapNotNull { declaration ->
                        val name = declaration.nameOrNull() ?: return@mapNotNull null
                        VisibilityRules.apiContractViolation(
                            packageName = packageName,
                            declarationName = name,
                            isPublic = declaration.isExplicitlyPublic(),
                        )?.let { "${file.projectPath}: $it" }
                    }
            }

        Assert.assertEquals(listOf<String>(), violations)
    }

    @Test
    fun modulePackagesMatchTheGradleModule() {
        val files = featureScope.files + designSystemScope.files + infrastructureScope.files +
            diScope.files + shellScope.files + platformScope.files
        val paths = files.map { it.projectPath.replace('\\', '/') }
        // The rule derives the module from the path, so a path without the `/src/` part, or a
        // path that does not start at the repository directory, would silently disable the check.
        Assert.assertTrue(
            "Expected repo-relative source paths: ${paths.take(3)}",
            paths.all { it.contains("/src/") },
        )

        val violations = files.mapNotNull { file ->
            ModulePackageRules.violation(file.projectPath, file.packagee?.name.orEmpty())
                ?.let { "${file.projectPath}: $it" }
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
                declarationKind = declaration.kind,
                annotations = declaration.annotations,
                modifiers = declaration.modifiers,
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
                    modifiers = declaration.modifierNames(),
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
        val modifiers: List<String>,
    )

    private fun KoBaseDeclaration.kind(): DeclarationKind = when (this) {
        is KoInterfaceDeclaration -> DeclarationKind.INTERFACE
        is KoObjectDeclaration -> DeclarationKind.OBJECT
        is KoClassDeclaration -> DeclarationKind.CLASS
        is KoFunctionDeclaration -> DeclarationKind.FUNCTION
        else -> DeclarationKind.OTHER
    }

    private fun KoBaseDeclaration.nameOrNull(): String? = (this as? KoNameProvider)?.name

    /** Modifier names such as `DATA`, `SEALED`, `ENUM` and `VALUE` that define a model role. */
    private fun KoBaseDeclaration.modifierNames(): List<String> =
        (this as? KoModifierProvider)?.modifiers?.map { it.name }.orEmpty()

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
