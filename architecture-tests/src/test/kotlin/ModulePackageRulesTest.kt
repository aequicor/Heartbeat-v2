import org.junit.Assert
import org.junit.Test

/**
 * Unit tests of the module placement rule (HB-ARCH-043): a source in a Gradle module may
 * declare only the package roles of that module, so an unrelated package cannot escape the
 * naming and visibility rules.
 */
class ModulePackageRulesTest {

    private val feature = "io.github.aequicor.heartbeat.features.editor"
    private val designSystem = "io.github.aequicor.heartbeat.designsystem"

    private val apiModule = "features/editor/api/src/commonMain/kotlin/EditorStore.kt"
    private val implModule = "features/editor/impl/src/commonMain/kotlin/Note.kt"
    private val adapterModule =
        "features/editor/presentation/decompose/src/commonMain/kotlin/EditorComponent.kt"
    private val uiModule = "features/editor/ui/compose/src/commonMain/kotlin/EditorScreen.kt"
    private val themeModule = "designsystem/compose/theme/src/commonMain/kotlin/HbColors.kt"

    private fun assertViolation(
        projectPath: String,
        packageName: String,
        modulePath: String,
        vararg fragments: String,
    ) {
        val message = ModulePackageRules.violation(projectPath, packageName)
            ?: error("Expected a violation for $packageName in $projectPath")
        Assert.assertTrue(
            "Wrong message: $message",
            message.startsWith("package $packageName does not belong to module $modulePath: "),
        )
        fragments.forEach { fragment ->
            Assert.assertTrue("Missing '$fragment' in: $message", fragment in message)
        }
    }

    @Test
    fun apiModuleDeclaresTheApiRoleAndItsSubpackages() {
        Assert.assertNull(ModulePackageRules.violation(apiModule, "$feature.api"))
        Assert.assertNull(ModulePackageRules.violation(apiModule, "$feature.api.model"))
        Assert.assertNull(ModulePackageRules.violation(apiModule, "$feature.api.contract"))

        assertViolation(apiModule, "$feature.impl", "features/editor/api", "$feature.api")
    }

    @Test
    fun implModuleDeclaresTheUniversalRoles() {
        listOf(".domain.usecase", ".data.repository", ".presentation", ".presentation.mapper", ".di")
            .forEach { suffix -> Assert.assertNull(ModulePackageRules.violation(implModule, "$feature$suffix")) }

        assertViolation(implModule, "$feature.api", "features/editor/impl", "$feature.api")
        assertViolation(implModule, "$feature.component", "features/editor/impl", "$feature.component")
        assertViolation(implModule, feature, "features/editor/impl", feature)
    }

    @Test
    fun unrelatedPackageInFeatureImplIsAViolation() {
        assertViolation(
            implModule,
            "com.example.unrelated",
            "features/editor/impl",
            "com.example.unrelated",
        )
    }

    @Test
    fun adapterAndUiModulesDeclareTheirOwnRoles() {
        Assert.assertNull(ModulePackageRules.violation(adapterModule, "$feature.component"))
        assertViolation(adapterModule, "$feature.presentation", "features/editor/presentation/decompose")

        Assert.assertNull(ModulePackageRules.violation(uiModule, "$feature.ui"))
        assertViolation(uiModule, "$feature.component", "features/editor/ui/compose")
    }

    @Test
    fun designSystemModuleDeclaresThePackageOfItsRole() {
        Assert.assertNull(ModulePackageRules.violation(themeModule, "$designSystem.theme"))
        Assert.assertNull(ModulePackageRules.violation(themeModule, "$designSystem.theme.tokens"))
        assertViolation(themeModule, "$designSystem.components", "designsystem/compose/theme")
    }

    @Test
    fun hyphenatedFeatureDirectoryDeclaresThePackageWithoutHyphen() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "features/ai-studio/ui/compose/src/commonMain/kotlin/AiStudioScreen.kt",
                "io.github.aequicor.heartbeat.features.aistudio.ui",
            ),
        )
        assertViolation(
            "features/ai-studio/ui/compose/src/commonMain/kotlin/AiStudioScreen.kt",
            "io.github.aequicor.heartbeat.features.ai-studio.ui",
            "features/ai-studio/ui/compose",
            "io.github.aequicor.heartbeat.features.aistudio.ui",
        )
    }

    @Test
    fun unknownFeatureModuleRoleIsAViolation() {
        Assert.assertEquals(
            "unknown feature module role: features/editor/extra",
            ModulePackageRules.violation(
                "features/editor/extra/src/commonMain/kotlin/Something.kt",
                "$feature.presentation",
            ),
        )
    }

    @Test
    fun coreModuleDeclaresThePackageOfItsDirectory() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "core/platform-api/src/commonMain/kotlin/Platform.kt",
                "io.github.aequicor.heartbeat.core.platformapi",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "core/network/src/commonMain/kotlin/HttpClient.kt",
                "io.github.aequicor.heartbeat.core.network",
            ),
        )
        assertViolation(
            "core/network/src/commonMain/kotlin/HttpClient.kt",
            "io.github.aequicor.heartbeat.core.database",
            "core/network",
            "io.github.aequicor.heartbeat.core.network",
        )
        assertViolation(
            "core/network/src/commonMain/kotlin/HttpClient.kt",
            "com.example.unrelated",
            "core/network",
        )
    }

    @Test
    fun nestedCoreModuleDeclaresItsFullPackage() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "core/preferences/api/src/commonMain/kotlin/PreferencesStore.kt",
                "io.github.aequicor.heartbeat.core.preferences.api",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "core/preferences/api/src/commonMain/kotlin/PreferencesStore.kt",
                "io.github.aequicor.heartbeat.core.preferences.api.model",
            ),
        )
        assertViolation(
            "core/preferences/api/src/commonMain/kotlin/PreferencesStore.kt",
            "io.github.aequicor.heartbeat.core.preferences.datastore",
            "core/preferences/api",
            "io.github.aequicor.heartbeat.core.preferences.api",
        )
    }

    @Test
    fun storageModuleDeclaresTheStoragePackage() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "storage/database/src/commonMain/kotlin/DatabaseInitializer.kt",
                "io.github.aequicor.heartbeat.storage.database",
            ),
        )
        assertViolation(
            "storage/database/src/commonMain/kotlin/DatabaseInitializer.kt",
            "io.github.aequicor.heartbeat.storage.other",
            "storage/database",
            "io.github.aequicor.heartbeat.storage.database",
        )
    }

    @Test
    fun diModuleDeclaresTheSharedDiPackage() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "di/api/src/commonMain/kotlin/Scopes.kt",
                "io.github.aequicor.heartbeat.di",
            ),
        )
        assertViolation(
            "di/api/src/commonMain/kotlin/Scopes.kt",
            "io.github.aequicor.heartbeat.features.editor",
            "di/api",
        )
        Assert.assertEquals(
            "unknown di module role: di/extra",
            ModulePackageRules.violation(
                "di/extra/src/commonMain/kotlin/Extra.kt",
                "io.github.aequicor.heartbeat.di",
            ),
        )
    }

    @Test
    fun shellModulesDeclareTheShellArea() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "shell/impl/src/commonMain/kotlin/Shell.kt",
                "io.github.aequicor.heartbeat.shell",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "shell/ui/compose/src/commonMain/kotlin/Shell.kt",
                "io.github.aequicor.heartbeat.shell.ui",
            ),
        )
        assertViolation(
            "shell/impl/src/commonMain/kotlin/Shell.kt",
            "com.example.unrelated",
            "shell/impl",
        )
    }

    @Test
    fun navigationModulesDeclareTheirOwnPackages() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "navigation/api/src/commonMain/kotlin/AppRoute.kt",
                "io.github.aequicor.heartbeat.navigation.api",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "navigation/decompose/src/commonMain/kotlin/FeatureEntry.kt",
                "io.github.aequicor.heartbeat.navigation.decompose",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "navigation/decompose-compose/src/commonMain/kotlin/Renderer.kt",
                "io.github.aequicor.heartbeat.navigation.compose",
            ),
        )
        assertViolation(
            "navigation/api/src/commonMain/kotlin/AppRoute.kt",
            "io.github.aequicor.heartbeat.navigation.decompose",
            "navigation/api",
            "io.github.aequicor.heartbeat.navigation.api",
        )
        Assert.assertEquals(
            "unknown navigation module role: navigation/extra",
            ModulePackageRules.violation(
                "navigation/extra/src/commonMain/kotlin/Extra.kt",
                "io.github.aequicor.heartbeat.navigation.api",
            ),
        )
    }

    @Test
    fun platformModulesDeclareTheRootPackageOnly() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "platform/desktop/src/main/kotlin/Main.kt",
                "io.github.aequicor.heartbeat",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "platform/desktop/src/main/kotlin/io/github/aequicor/heartbeat/di/DesktopGraph.kt",
                "io.github.aequicor.heartbeat.di",
            ),
        )
        assertViolation(
            "platform/desktop/src/main/kotlin/Main.kt",
            "io.github.aequicor.heartbeat.features.editor",
            "platform/desktop",
        )
    }

    @Test
    fun aiEngineModulesDeclareTheirPathPackages() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "ai/engine/api/src/commonMain/kotlin/AiEngineFacade.kt",
                "io.github.aequicor.heartbeat.ai.engine.api",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "ai/engine/impl/src/commonMain/kotlin/DefaultAiEngineFacade.kt",
                "io.github.aequicor.heartbeat.ai.engine.impl",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "ai/engine/acp/src/main/kotlin/AcpAdapter.kt",
                "io.github.aequicor.heartbeat.ai.engine.acp",
            ),
        )
        Assert.assertNull(
            ModulePackageRules.violation(
                "ai/engines/deepseek-harness/src/main/kotlin/DeepSeekHarnessProvider.kt",
                "io.github.aequicor.heartbeat.ai.engines.deepseekharness",
            ),
        )
        assertViolation(
            "ai/engine/acp/src/main/kotlin/AcpAdapter.kt",
            "io.github.aequicor.heartbeat.features.aistudio.api",
            "ai/engine/acp",
            "io.github.aequicor.heartbeat.ai.engine.acp",
        )
        Assert.assertEquals(
            "unknown ai module role: ai/support",
            ModulePackageRules.violation(
                "ai/support/src/main/kotlin/Support.kt",
                "io.github.aequicor.heartbeat.ai.support",
            ),
        )
    }

    @Test
    fun pathsOutsideArchitectureAreasHaveNoRule() {
        Assert.assertNull(
            ModulePackageRules.violation("buildSrc/src/main/kotlin/Plugin.gradle.kts", "plugin"),
        )
        Assert.assertNull(ModulePackageRules.violation("docs/README.md", "docs"))
    }

    @Test
    fun windowsSeparatorsAreNormalized() {
        Assert.assertNull(
            ModulePackageRules.violation(
                "features\\editor\\impl\\src\\commonMain\\kotlin\\Note.kt",
                "$feature.domain.model",
            ),
        )
    }
}
