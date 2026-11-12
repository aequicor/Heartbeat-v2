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
        NavigationApi,
        NavigationDecompose,
        NavigationCompose,
        FeatureApi,
        FeatureImpl,
        FeaturePresentation,
        FeatureUi,
        Storage,
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

    /** Compose without the lifecycle artifacts, which Decompose itself brings transitively. */
    private val composeArtifactGroups = listOf(
        "org.jetbrains.compose",
        "androidx.compose",
    )

    private val allowedTargetsByRole: Map<Role, Set<Role>> = mapOf(
        // The composition root lives in the platform compilation and sees every binding.
        Role.Platform to Role.entries.toSet(),
        Role.ShellApi to setOf(
            Role.ShellApi,
            Role.FeatureApi,
            Role.NavigationApi,
            Role.DiApi,
            Role.Core,
        ),
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
            Role.NavigationApi,
            Role.NavigationDecompose,
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
            Role.NavigationApi,
            Role.NavigationCompose,
            Role.NavigationDecompose,
            Role.DesignSystem,
            Role.DiApi,
            Role.Core,
        ),
        Role.NavigationApi to setOf(Role.NavigationApi),
        Role.NavigationDecompose to setOf(Role.NavigationApi, Role.NavigationDecompose),
        Role.NavigationCompose to setOf(Role.NavigationApi, Role.NavigationDecompose),
        Role.FeatureApi to setOf(Role.FeatureApi, Role.NavigationApi, Role.Core),
        Role.FeatureImpl to setOf(Role.FeatureApi, Role.NavigationApi, Role.DiApi, Role.Core),
        Role.FeaturePresentation to setOf(
            Role.FeatureApi,
            Role.NavigationApi,
            Role.NavigationDecompose,
            Role.DiApi,
            Role.Core,
        ),
        Role.FeatureUi to setOf(
            Role.FeatureApi,
            Role.FeaturePresentation,
            Role.NavigationApi,
            Role.NavigationCompose,
            Role.DesignSystem,
            Role.DiApi,
            Role.Core,
        ),
        // Schema composition is the one reason storage depends on feature implementations.
        // Features never depend on storage: they receive the connection through core contracts.
        Role.Storage to setOf(
            Role.Storage,
            Role.FeatureImpl,
            Role.DiApi,
            Role.Core,
        ),
        Role.DiApi to setOf(Role.DiApi, Role.Core),
        Role.Core to setOf(Role.Core, Role.DiApi),
        Role.DesignSystem to setOf(Role.DesignSystem, Role.Core),
        Role.Other to setOf(Role.Other),
    )

    /**
     * Framework groups a role must not declare directly. Compose UI of a feature is the
     * exception: it renders its own Decompose adapter through the renderer SPI and contributes the
     * renderer with Metro, so those frameworks are expected there.
     */
    private val baseForbiddenGroups: Map<Role, List<String>> = mapOf(
        Role.ShellApi to uiFrameworkGroups + METRO_GROUP,
        Role.ShellImpl to uiFrameworkGroups,
        Role.NavigationApi to uiFrameworkGroups + METRO_GROUP,
        Role.NavigationDecompose to composeFrameworkGroups + METRO_GROUP,
        Role.NavigationCompose to listOf(METRO_GROUP),
        Role.FeatureApi to uiFrameworkGroups + METRO_GROUP,
        Role.FeatureImpl to uiFrameworkGroups,
        Role.FeaturePresentation to composeFrameworkGroups,
        Role.ShellPresentation to composeFrameworkGroups,
        Role.FeatureUi to uiFrameworkGroups + METRO_GROUP,
        Role.ShellUi to listOf(METRO_GROUP),
        // Storage owns persistence and Metro bindings, but never renders UI.
        Role.Storage to uiFrameworkGroups,
        Role.DiApi to uiFrameworkGroups,
        Role.Core to uiFrameworkGroups + METRO_GROUP,
        Role.DesignSystem to listOf(DECOMPOSE_GROUP, METRO_GROUP),
        Role.Platform to listOf(),
        Role.Other to listOf(),
    )

    /**
     * Framework groups a role must not have on its resolved compile and runtime graph. The
     * transitive policy differs from the direct one where a framework arrives through an allowed
     * dependency: a presentation adapter may inherit the `androidx.lifecycle` artifacts from
     * Decompose, but declaring them directly stays forbidden.
     */
    private fun transitiveForbiddenGroups(path: String): List<String> = when (role(path)) {
        Role.FeaturePresentation, Role.ShellPresentation -> composeArtifactGroups
        // Decompose brings `androidx.lifecycle` transitively; only Compose artifacts stay banned.
        Role.NavigationDecompose -> composeArtifactGroups + METRO_GROUP
        else -> declaredForbiddenGroups(path)
    }

    private fun declaredForbiddenGroups(path: String): List<String> =
        if (isComposeUi(path)) emptyList() else baseForbiddenGroups.getValue(role(path))

    /** `features/<feature>/ui/compose` renders the Decompose flow, unlike a native UI module. */
    private fun isComposeUi(path: String): Boolean {
        val segments = pathSegments(path)
        val index = segments.indexOf("ui")
        return index >= 0 && segments.getOrNull(index + 1)?.startsWith("compose") == true
    }

    fun pathSegments(path: String): List<String> =
        path.split(":").filter { it.isNotEmpty() }

    fun isFeature(path: String): Boolean = path.startsWith(":features:")

    fun isApi(path: String): Boolean =
        path == ":shell:api" ||
            (isFeature(path) && pathSegments(path).last() == "api")

    fun isImpl(path: String): Boolean =
        path == ":shell:impl" ||
            (isFeature(path) && pathSegments(path).last() == "impl") ||
            path == ":features:engines:dsh"

    fun role(path: String): Role = when {
        path.startsWith(":platform:") -> Role.Platform
        path == ":shell:api" -> Role.ShellApi
        path == ":shell:impl" -> Role.ShellImpl
        path.startsWith(":shell:presentation:") -> Role.ShellPresentation
        path.startsWith(":shell:ui:") -> Role.ShellUi
        path == ":navigation:api" -> Role.NavigationApi
        path == ":navigation:decompose" -> Role.NavigationDecompose
        path == ":navigation:decompose-compose" -> Role.NavigationCompose
        path.startsWith(":storage:") -> Role.Storage
        isFeature(path) && isApi(path) -> Role.FeatureApi
        isFeature(path) && isImpl(path) -> Role.FeatureImpl
        isFeature(path) && pathSegments(path).contains("presentation") -> Role.FeaturePresentation
        isFeature(path) && pathSegments(path).contains("ui") -> Role.FeatureUi
        path.startsWith(":di:") -> Role.DiApi
        path.startsWith(":core") -> Role.Core
        path.startsWith(":designsystem") -> Role.DesignSystem
        else -> Role.Other
    }

    /**
     * Modules whose resolved graphs are checked for forbidden transitive frameworks. Every role
     * that forbids a framework group is checked: the framework can arrive through an allowed
     * intermediate library (for example Compose through a presentation adapter dependency), so
     * declared dependencies alone do not protect the boundary. The declared-dependency pass and
     * the resolved-graph pass stay separate checks with separate policies
     * ([moduleDependencyViolation] and [transitiveModuleDependencyViolation]).
     */
    fun requiresCleanClasspath(path: String): Boolean =
        transitiveForbiddenGroups(path).isNotEmpty()

    fun allowedTargets(from: String): Set<Role> = allowedTargetsByRole.getValue(role(from))

    fun isAllowedDependency(fromPath: String, toPath: String): Boolean {
        val from = role(fromPath)
        val to = role(toPath)
        if (to !in allowedTargetsByRole.getValue(from)) {
            return false
        }
        if (from == Role.FeatureUi && to == Role.FeaturePresentation) {
            return featureOf(fromPath) == featureOf(toPath)
        }
        return true
    }

    fun projectDependencyViolation(fromPath: String, toPath: String): String? {
        val from = role(fromPath)
        val to = role(toPath)
        if (isAllowedDependency(fromPath, toPath)) {
            return null
        }
        if (from == Role.FeatureUi && to == Role.FeaturePresentation) {
            return "ui may depend only on the presentation adapter of the same feature"
        }
        if (from == Role.FeatureImpl && to == Role.Storage) {
            return "features must not depend on storage"
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

            from == Role.Core -> "core must not depend on features, shell or designsystem"

            from == Role.DesignSystem && to == Role.DiApi ->
                "designsystem must not depend on Metro scopes"

            from == Role.DesignSystem -> "designsystem must not depend on features, shell or di"

            else -> "${from.name} must not depend on ${to.name}: $fromPath -> $toPath"
        }
    }

    /** Violation of the declared-dependency policy of the module role. */
    fun moduleDependencyViolation(modulePath: String, group: String): String? =
        groupViolation(declaredForbiddenGroups(modulePath), role(modulePath), group)

    /** Violation of the resolved-graph policy of the module role. */
    fun transitiveModuleDependencyViolation(modulePath: String, group: String): String? =
        groupViolation(transitiveForbiddenGroups(modulePath), role(modulePath), group)

    private fun groupViolation(
        policy: List<String>,
        moduleRole: Role,
        group: String,
    ): String? {
        val forbidden = policy.firstOrNull { isGroup(group, it) }
            ?: return null
        return "${roleDescription(moduleRole)} must not depend on ${reason(forbidden)}"
    }

    fun forbiddenImport(modulePath: String, importPath: String): String? {
        val forbidden = declaredForbiddenGroups(modulePath)
            .firstOrNull { isGroup(importPath, it) }
            ?: return null
        return "${roleDescription(role(modulePath))} must not import ${reason(forbidden)}: $importPath"
    }

    private fun featureOf(path: String): String? =
        pathSegments(path).takeIf { it.firstOrNull() == "features" }?.getOrNull(1)

    private fun roleDescription(role: Role): String = when (role) {
        Role.FeatureApi, Role.ShellApi -> "api contracts"
        Role.FeatureImpl, Role.ShellImpl -> "impl"
        Role.Core -> "core"
        Role.DiApi -> "di"
        Role.FeatureUi, Role.ShellUi -> "ui"
        Role.DesignSystem -> "designsystem"
        Role.FeaturePresentation, Role.ShellPresentation -> "presentation adapters"
        Role.NavigationApi, Role.NavigationDecompose, Role.NavigationCompose -> "navigation"
        Role.Storage -> "storage"
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

