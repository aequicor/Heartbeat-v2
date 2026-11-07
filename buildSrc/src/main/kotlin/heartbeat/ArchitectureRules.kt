package heartbeat

/**
 * Dependency, module and import rules from docs/specs/architecture.md.
 *
 * Every Gradle module has an architecture [Role], and each role lists the roles it may depend
 * on. The allowlist is the single source of truth: an edge that is not listed is a violation,
 * so a new module cannot reach an implementation through an unhandled pair. The same role
 * decides which framework groups may appear on the module classpath and which imports are
 * allowed in its sources.
 *
 * The functions are pure so the rules stay unit-testable; the Gradle task only feeds them
 * declared dependencies, resolved classpaths and source imports. Naming and package-role
 * conventions live in the Konsist tests in `:architecture-tests`.
 */
object ArchitectureRules {

    /** Architecture role of a Gradle module. */
    enum class Role {
        Platform,
        ShellApi,
        ShellImpl,
        ShellPresentation,
        ShellUi,
        FeatureApi,
        FeatureImpl,
        FeaturePresentation,
        FeatureUi,
        DiApi,
        Core,
        DesignSystem,
        Other,
    }

    private const val DECOMPOSE_GROUP = "com.arkivanov.decompose"

    private const val METRO_GROUP = "dev.zacsweers.metro"

    private val uiFrameworkGroups = listOf(
        DECOMPOSE_GROUP,
        "org.jetbrains.compose",
        "androidx.compose",
        "androidx.lifecycle",
    )

    private val composeFrameworkGroups = listOf(
        "org.jetbrains.compose",
        "androidx.compose",
        "androidx.lifecycle",
    )

    /** Roles whose resolved compile and runtime graphs must stay free of UI frameworks. */
    private val cleanClasspathRoles = setOf(
        Role.FeatureApi,
        Role.ShellApi,
        Role.FeatureImpl,
        Role.ShellImpl,
        Role.DiApi,
        Role.Core,
    )

    private val allowedTargetsByRole: Map<Role, Set<Role>> = mapOf(
        // The composition root lives in the platform compilation and sees every binding.
        Role.Platform to Role.entries.toSet(),
        Role.ShellApi to setOf(Role.ShellApi, Role.FeatureApi, Role.DiApi, Role.Core),
        Role.ShellImpl to setOf(
            Role.ShellApi,
            Role.ShellImpl,
            Role.FeatureApi,
            Role.DiApi,
            Role.Core,
        ),
        Role.ShellPresentation to setOf(
            Role.ShellApi,
            Role.ShellImpl,
            Role.ShellPresentation,
            Role.FeatureApi,
            Role.FeaturePresentation,
            Role.DiApi,
            Role.Core,
        ),
        Role.ShellUi to setOf(
            Role.ShellApi,
            Role.ShellImpl,
            Role.ShellPresentation,
            Role.ShellUi,
            Role.FeatureApi,
            Role.FeaturePresentation,
            Role.FeatureUi,
            Role.DesignSystem,
            Role.DiApi,
            Role.Core,
        ),
        Role.FeatureApi to setOf(Role.FeatureApi, Role.Core),
        Role.FeatureImpl to setOf(Role.FeatureApi, Role.DiApi, Role.Core),
        Role.FeaturePresentation to setOf(Role.FeatureApi, Role.DiApi, Role.Core),
        Role.FeatureUi to setOf(Role.FeatureApi, Role.DesignSystem, Role.Core),
        Role.DiApi to setOf(Role.DiApi, Role.Core),
        Role.Core to setOf(Role.Core, Role.DiApi),
        Role.DesignSystem to setOf(Role.DesignSystem, Role.Core),
        Role.Other to setOf(Role.Other),
    )

    private val forbiddenGroups: Map<Role, List<String>> = mapOf(
        Role.FeatureApi to uiFrameworkGroups + METRO_GROUP,
        Role.ShellApi to uiFrameworkGroups + METRO_GROUP,
        Role.FeatureImpl to uiFrameworkGroups,
        Role.ShellImpl to uiFrameworkGroups,
        Role.DiApi to uiFrameworkGroups,
        Role.Core to uiFrameworkGroups + METRO_GROUP,
        Role.FeaturePresentation to composeFrameworkGroups,
        Role.ShellPresentation to composeFrameworkGroups,
        Role.FeatureUi to listOf(DECOMPOSE_GROUP, METRO_GROUP),
        Role.ShellUi to listOf(METRO_GROUP),
        Role.DesignSystem to listOf(DECOMPOSE_GROUP, METRO_GROUP),
        Role.Platform to listOf(),
        Role.Other to listOf(),
    )

    private val forbiddenImports: Map<Role, List<String>> = mapOf(
        Role.FeatureApi to uiFrameworkGroups + METRO_GROUP,
        Role.ShellApi to uiFrameworkGroups + METRO_GROUP,
        Role.FeatureImpl to uiFrameworkGroups,
        Role.ShellImpl to uiFrameworkGroups,
        Role.DiApi to uiFrameworkGroups,
        Role.Core to uiFrameworkGroups + METRO_GROUP,
        Role.FeaturePresentation to composeFrameworkGroups,
        Role.ShellPresentation to composeFrameworkGroups,
        Role.FeatureUi to listOf(DECOMPOSE_GROUP, METRO_GROUP),
        Role.ShellUi to listOf(METRO_GROUP),
        Role.DesignSystem to listOf(DECOMPOSE_GROUP, METRO_GROUP),
        Role.Platform to listOf(),
        Role.Other to listOf(),
    )

    fun pathSegments(path: String): List<String> =
        path.split(":").filter { it.isNotEmpty() }

    fun isFeature(path: String): Boolean = path.startsWith(":features:")

    fun isApi(path: String): Boolean =
        path == ":shell:api" ||
            (isFeature(path) && pathSegments(path).last() == "api")

    fun isImpl(path: String): Boolean =
        path == ":shell:impl" ||
            (isFeature(path) && pathSegments(path).last() == "impl")

    fun role(path: String): Role = when {
        path.startsWith(":platform:") -> Role.Platform
        path == ":shell:api" -> Role.ShellApi
        path == ":shell:impl" -> Role.ShellImpl
        path.startsWith(":shell:presentation:") -> Role.ShellPresentation
        path.startsWith(":shell:ui:") -> Role.ShellUi
        isFeature(path) && isApi(path) -> Role.FeatureApi
        isFeature(path) && isImpl(path) -> Role.FeatureImpl
        isFeature(path) && pathSegments(path).contains("presentation") -> Role.FeaturePresentation
        isFeature(path) && pathSegments(path).contains("ui") -> Role.FeatureUi
        path.startsWith(":di:") -> Role.DiApi
        path.startsWith(":core") -> Role.Core
        path.startsWith(":designsystem") -> Role.DesignSystem
        else -> Role.Other
    }

    /** Modules whose resolved graphs are checked for transitive UI frameworks. */
    fun requiresCleanClasspath(path: String): Boolean = role(path) in cleanClasspathRoles

    fun allowedTargets(from: String): Set<Role> = allowedTargetsByRole.getValue(role(from))

    fun isAllowedDependency(fromPath: String, toPath: String): Boolean =
        role(toPath) in allowedTargetsByRole.getValue(role(fromPath))

    fun projectDependencyViolation(fromPath: String, toPath: String): String? {
        val from = role(fromPath)
        val to = role(toPath)
        if (to in allowedTargetsByRole.getValue(from)) {
            return null
        }
        return when {
            from == Role.FeatureApi -> "api modules depend only on api contracts and core"

            from == Role.FeatureImpl && to == Role.FeatureImpl ->
                "features interact only through api modules"

            from == Role.FeatureImpl -> "impl must not depend on presentation, ui or designsystem"

            from == Role.FeaturePresentation && to == Role.FeatureImpl ->
                "presentation receives the Store through api and must not depend on impl"

            from == Role.FeaturePresentation && to == Role.FeaturePresentation ->
                "presentation adapters must not depend on each other"

            from == Role.FeatureUi && to == Role.FeatureImpl -> "ui must not depend on impl"

            from == Role.FeatureUi && to == Role.FeaturePresentation ->
                "ui is presentation-framework independent; only shell:ui:compose may bind components"

            from == Role.Core -> "core must not depend on features, shell or designsystem"

            from == Role.DesignSystem && to == Role.DiApi ->
                "designsystem must not depend on Metro scopes"

            from == Role.DesignSystem -> "designsystem must not depend on features, shell or di"

            else -> "${from.name} must not depend on ${to.name}: $fromPath -> $toPath"
        }
    }

    fun moduleDependencyViolation(modulePath: String, group: String): String? {
        val moduleRole = role(modulePath)
        val forbidden = forbiddenGroups.getValue(moduleRole).firstOrNull { isGroup(group, it) }
            ?: return null
        return "${roleDescription(moduleRole)} must not depend on ${reason(forbidden)}"
    }

    fun forbiddenImport(modulePath: String, importPath: String): String? {
        val forbidden = forbiddenImports.getValue(role(modulePath))
            .firstOrNull { isGroup(importPath, it) }
            ?: return null
        return "${roleDescription(role(modulePath))} must not import ${reason(forbidden)}: $importPath"
    }

    private fun roleDescription(role: Role): String = when (role) {
        Role.FeatureApi, Role.ShellApi -> "api contracts"
        Role.FeatureImpl, Role.ShellImpl -> "impl"
        Role.Core -> "core"
        Role.DiApi -> "di"
        Role.FeatureUi, Role.ShellUi -> "ui"
        Role.DesignSystem -> "designsystem"
        Role.FeaturePresentation, Role.ShellPresentation -> "presentation adapters"
        Role.Platform -> "platform"
        Role.Other -> "module"
    }

    private fun reason(group: String): String = when {
        group == METRO_GROUP -> "Metro"
        group == DECOMPOSE_GROUP -> "Decompose"
        group.startsWith("org.jetbrains.compose") || group.startsWith("androidx.compose") -> "Compose"
        else -> "UI or presentation frameworks"
    }

    /** Runtime and compile graphs of JVM, Android and Kotlin/Native targets. */
    fun isRelevantClasspathConfiguration(name: String, canBeResolved: Boolean): Boolean =
        canBeResolved && (
            name.endsWith("RuntimeClasspath") ||
                name.endsWith("CompileClasspath") ||
                name.endsWith("CompileKlibraries")
            )

    private fun isGroup(group: String, forbidden: String): Boolean =
        group == forbidden || group.startsWith("$forbidden.")
}

