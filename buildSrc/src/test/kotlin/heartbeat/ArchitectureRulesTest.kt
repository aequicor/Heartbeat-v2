package heartbeat

import heartbeat.ArchitectureRules.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchitectureRulesTest {

    /** One representative module per architecture role. */
    private val roleSamples = listOf(
        ":platform:desktop" to Role.Platform,
        ":shell:api" to Role.ShellApi,
        ":shell:impl" to Role.ShellImpl,
        ":shell:presentation:decompose" to Role.ShellPresentation,
        ":shell:ui:compose" to Role.ShellUi,
        ":features:editor:api" to Role.FeatureApi,
        ":features:editor:impl" to Role.FeatureImpl,
        ":features:editor:presentation:decompose" to Role.FeaturePresentation,
        ":features:editor:ui:compose" to Role.FeatureUi,
        ":di:api" to Role.DiApi,
        ":core:network" to Role.Core,
        ":designsystem:compose:components" to Role.DesignSystem,
        ":architecture-tests" to Role.Other,
    )

    @Test
    fun everyRoleIsRecognised() {
        roleSamples.forEach { (path, expected) ->
            assertEquals(expected, ArchitectureRules.role(path), path)
        }
    }

    /**
     * The allowlist is the contract: every role pair either passes or fails, so a new module
     * cannot reach an implementation through an unhandled edge.
     */
    @Test
    fun everyRolePairFollowsTheAllowlist() {
        roleSamples.forEach { (fromPath, _) ->
            roleSamples.forEach { (toPath, toRole) ->
                val violation = ArchitectureRules.projectDependencyViolation(fromPath, toPath)
                val allowed = toRole in ArchitectureRules.allowedTargets(fromPath)
                if (allowed) {
                    assertNull(violation, "$fromPath -> $toPath must be allowed")
                } else {
                    assertNotNull(violation, "$fromPath -> $toPath must be forbidden")
                }
            }
        }
    }

    @Test
    fun presentationMustNotDependOnImpl() {
        assertEquals(
            "presentation receives the Store through api and must not depend on impl",
            ArchitectureRules.projectDependencyViolation(
                ":features:editor:presentation:decompose",
                ":features:editor:impl",
            ),
        )
    }

    @Test
    fun apiMustNotDependOnImpl() {
        assertEquals(
            "api modules depend only on api contracts and core",
            ArchitectureRules.projectDependencyViolation(":features:editor:api", ":features:editor:impl"),
        )
    }

    @Test
    fun apiMustNotDependOnMetroScopes() {
        assertEquals(
            "api modules depend only on api contracts and core",
            ArchitectureRules.projectDependencyViolation(":features:editor:api", ":di:api"),
        )
    }

    @Test
    fun implMayDependOnApiAndDi() {
        assertNull(
            ArchitectureRules.projectDependencyViolation(
                ":features:editor:impl",
                ":features:editor:api",
            ),
        )
        assertNull(
            ArchitectureRules.projectDependencyViolation(":features:editor:impl", ":di:api"),
        )
        assertNull(
            ArchitectureRules.projectDependencyViolation(":features:editor:impl", ":core:platform-api"),
        )
    }

    @Test
    fun featuresInteractOnlyThroughApi() {
        assertNull(
            ArchitectureRules.projectDependencyViolation(":features:editor:impl", ":features:home:api"),
        )
        assertEquals(
            "features interact only through api modules",
            ArchitectureRules.projectDependencyViolation(
                ":features:editor:impl",
                ":features:home:impl",
            ),
        )
    }

    @Test
    fun presentationAdaptersMustNotDependOnEachOther() {
        assertEquals(
            "presentation adapters must not depend on each other",
            ArchitectureRules.projectDependencyViolation(
                ":features:editor:presentation:decompose",
                ":features:home:presentation:decompose",
            ),
        )
    }

    @Test
    fun uiMustNotDependOnImpl() {
        assertEquals(
            "ui must not depend on impl",
            ArchitectureRules.projectDependencyViolation(
                ":features:home:ui:compose",
                ":features:editor:impl",
            ),
        )
        assertEquals(
            "ui is presentation-framework independent; only shell:ui:compose may bind components",
            ArchitectureRules.projectDependencyViolation(
                ":features:editor:ui:compose",
                ":features:editor:presentation:decompose",
            ),
        )
    }

    @Test
    fun uiMayBindComponentsOnlyInShell() {
        assertNull(
            ArchitectureRules.projectDependencyViolation(
                ":shell:ui:compose",
                ":shell:presentation:decompose",
            ),
        )
    }

    @Test
    fun coreAndDesignSystemMustNotDependOnFeatures() {
        assertEquals(
            "core must not depend on features, shell or designsystem",
            ArchitectureRules.projectDependencyViolation(":core:network", ":features:editor:api"),
        )
        assertEquals(
            "designsystem must not depend on features, shell or di",
            ArchitectureRules.projectDependencyViolation(
                ":designsystem:compose:components",
                ":features:editor:api",
            ),
        )
    }

    @Test
    fun platformSeesEveryBinding() {
        roleSamples.forEach { (toPath, _) ->
            assertNull(
                ArchitectureRules.projectDependencyViolation(":platform:desktop", toPath),
                toPath,
            )
        }
    }

    @Test
    fun moduleGroupsAreCheckedPerRole() {
        assertEquals(
            "api contracts must not depend on Metro",
            ArchitectureRules.moduleDependencyViolation(":features:editor:api", "dev.zacsweers.metro"),
        )
        assertEquals(
            "api contracts must not depend on Compose",
            ArchitectureRules.moduleDependencyViolation(":features:editor:api", "org.jetbrains.compose.foundation"),
        )
        assertEquals(
            "core must not depend on Compose",
            ArchitectureRules.moduleDependencyViolation(":core:network", "org.jetbrains.compose"),
        )
        assertEquals(
            "designsystem must not depend on Metro",
            ArchitectureRules.moduleDependencyViolation(
                ":designsystem:compose:components",
                "dev.zacsweers.metro",
            ),
        )
        assertEquals(
            "ui must not depend on Decompose",
            ArchitectureRules.moduleDependencyViolation(
                ":features:editor:ui:compose",
                "com.arkivanov.decompose",
            ),
        )
        assertNull(
            ArchitectureRules.moduleDependencyViolation(":features:editor:impl", "dev.zacsweers.metro"),
        )
        assertNull(
            ArchitectureRules.moduleDependencyViolation(":di:api", "dev.zacsweers.metro"),
        )
        assertNull(
            ArchitectureRules.moduleDependencyViolation(":platform:desktop", "org.jetbrains.compose"),
        )
    }

    @Test
    fun universalModuleForbidsComposeImport() {
        assertEquals(
            "impl must not import Compose: androidx.compose.runtime.Composable",
            ArchitectureRules.forbiddenImport(
                ":features:editor:impl",
                "androidx.compose.runtime.Composable",
            ),
        )
    }

    @Test
    fun apiContractForbidsMetroImport() {
        assertEquals(
            "api contracts must not import Metro: dev.zacsweers.metro.Inject",
            ArchitectureRules.forbiddenImport(":features:editor:api", "dev.zacsweers.metro.Inject"),
        )
    }

    @Test
    fun presentationAdapterMayImportDecompose() {
        assertNull(
            ArchitectureRules.forbiddenImport(
                ":features:editor:presentation:decompose",
                "com.arkivanov.decompose.ComponentContext",
            ),
        )
    }

    @Test
    fun uiMayImportComposeButNotDecompose() {
        assertNull(
            ArchitectureRules.forbiddenImport(
                ":features:editor:ui:compose",
                "androidx.compose.runtime.Composable",
            ),
        )
        assertEquals(
            "ui must not import Decompose: com.arkivanov.decompose.ComponentContext",
            ArchitectureRules.forbiddenImport(
                ":features:editor:ui:compose",
                "com.arkivanov.decompose.ComponentContext",
            ),
        )
    }

    @Test
    fun presentationInheritsLifecycleButMustNotDeclareIt() {
        assertEquals(
            "presentation adapters must not depend on UI or presentation frameworks",
            ArchitectureRules.moduleDependencyViolation(
                ":features:editor:presentation:decompose",
                "androidx.lifecycle",
            ),
        )
        assertNull(
            ArchitectureRules.transitiveModuleDependencyViolation(
                ":features:editor:presentation:decompose",
                "androidx.lifecycle",
            ),
        )
        assertEquals(
            "presentation adapters must not depend on Compose",
            ArchitectureRules.transitiveModuleDependencyViolation(
                ":features:editor:presentation:decompose",
                "org.jetbrains.compose.foundation",
            ),
        )
    }

    @Test
    fun transitivePolicyStillBansFrameworksForCleanRoles() {
        assertEquals(
            "api contracts must not depend on Metro",
            ArchitectureRules.transitiveModuleDependencyViolation(
                ":features:editor:api",
                "dev.zacsweers.metro",
            ),
        )
        assertEquals(
            "impl must not depend on Compose",
            ArchitectureRules.transitiveModuleDependencyViolation(
                ":features:editor:impl",
                "org.jetbrains.compose.runtime",
            ),
        )
        assertEquals(
            "designsystem must not depend on Decompose",
            ArchitectureRules.transitiveModuleDependencyViolation(
                ":designsystem:compose:components",
                "com.arkivanov.decompose",
            ),
        )
        assertNull(
            ArchitectureRules.transitiveModuleDependencyViolation(
                ":platform:desktop",
                "dev.zacsweers.metro",
            ),
        )
    }

    @Test
    fun frameworkBansCoverTheResolvedGraphOfEveryGuardedRole() {
        // A framework ban protects the boundary only if it applies to the resolved graph as well:
        // the forbidden group can arrive through an allowed intermediate library.
        roleSamples.forEach { (path, role) ->
            val guarded = role != Role.Platform && role != Role.Other
            assertEquals(guarded, ArchitectureRules.requiresCleanClasspath(path), path)
        }
    }

    @Test
    fun classpathConfigurationSelectionCoversJvmAndroidAndNative() {
        assertTrue(ArchitectureRules.isRelevantClasspathConfiguration("jvmRuntimeClasspath", true))
        assertTrue(ArchitectureRules.isRelevantClasspathConfiguration("jvmCompileClasspath", true))
        assertTrue(
            ArchitectureRules.isRelevantClasspathConfiguration(
                "androidMainCompileClasspath",
                true,
            ),
        )
        assertTrue(
            ArchitectureRules.isRelevantClasspathConfiguration(
                "linuxX64CompileKlibraries",
                true,
            ),
        )
        assertFalse(ArchitectureRules.isRelevantClasspathConfiguration("jvmRuntimeClasspath", false))
        assertFalse(
            ArchitectureRules.isRelevantClasspathConfiguration(
                "commonMainImplementation",
                true,
            ),
        )
    }
}
