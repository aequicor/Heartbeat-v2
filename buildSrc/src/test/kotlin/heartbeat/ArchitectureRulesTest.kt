package heartbeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchitectureRulesTest {

    @Test
    fun apiMustNotDependOnImpl() {
        assertEquals(
            "api modules depend only on api, core and di contracts",
            ArchitectureRules.projectDependencyViolation(":features:editor:api", ":features:editor:impl"),
        )
    }

    @Test
    fun implMayDependOnApi() {
        assertNull(
            ArchitectureRules.projectDependencyViolation(
                ":features:editor:impl",
                ":features:editor:api",
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
    }

    @Test
    fun uiMayBindComponentsOnlyInShell() {
        assertNull(
            ArchitectureRules.projectDependencyViolation(
                ":shell:ui:compose",
                ":shell:presentation:decompose",
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
    fun apiContractsForbidComposeLibraries() {
        assertEquals(
            "api contracts must not depend on UI or presentation frameworks",
            ArchitectureRules.moduleDependencyViolation(":features:editor:api", "org.jetbrains.compose.foundation"),
        )
    }

    @Test
    fun implForbidsDecompose() {
        assertEquals(
            "impl must not depend on UI or presentation frameworks",
            ArchitectureRules.moduleDependencyViolation(":features:editor:impl", "com.arkivanov.decompose"),
        )
    }

    @Test
    fun implAllowsCoroutines() {
        assertNull(
            ArchitectureRules.moduleDependencyViolation(
                ":features:editor:impl",
                "org.jetbrains.kotlinx",
            ),
        )
    }

    @Test
    fun universalModuleForbidsComposeImport() {
        assertEquals(
            "universal modules must not import UI frameworks: androidx.compose.runtime.Composable",
            ArchitectureRules.forbiddenImport(
                ":features:editor:impl",
                "androidx.compose.runtime.Composable",
            ),
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
    fun classpathConfigurationSelectionCoversJvmAndroidAndNative() {
        assertTrue(ArchitectureRules.isRelevantClasspathConfiguration("jvmRuntimeClasspath", true))
        assertTrue(ArchitectureRules.isRelevantClasspathConfiguration("jvmCompileClasspath", true))
        assertTrue(ArchitectureRules.isRelevantClasspathConfiguration("androidMainCompileClasspath", true))
        assertTrue(ArchitectureRules.isRelevantClasspathConfiguration("linuxX64CompileKlibraries", true))
        assertFalse(ArchitectureRules.isRelevantClasspathConfiguration("jvmRuntimeClasspath", false))
        assertFalse(ArchitectureRules.isRelevantClasspathConfiguration("commonMainImplementation", true))
    }
}
