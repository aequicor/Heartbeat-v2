package heartbeat

/**
 * Dependency, naming and import rules from docs/specs/architecture.md.
 *
 * The functions are pure so the rules stay unit-testable; the Gradle task only
 * feeds them declared dependencies, resolved classpaths and source declarations.
 */
object ArchitectureRules {

    private val forbiddenUiGroups = listOf(
        "com.arkivanov.decompose",
        "org.jetbrains.compose",
        "androidx.compose",
        "androidx.lifecycle",
    )

    private val forbiddenImports = listOf(
        "com.arkivanov.decompose",
        "org.jetbrains.compose",
        "androidx.compose",
        "androidx.lifecycle.ViewModel",
    )

    fun pathSegments(path: String): List<String> =
        path.split(":").filter { it.isNotEmpty() }

    fun isApi(path: String): Boolean =
        path == ":shell:api" ||
            (path.startsWith(":features:") && pathSegments(path).last() == "api")

    fun isImpl(path: String): Boolean =
        path == ":shell:impl" ||
            (path.startsWith(":features:") && pathSegments(path).last() == "impl")

    fun isPresentation(path: String): Boolean = pathSegments(path).contains("presentation")

    fun isUi(path: String): Boolean = pathSegments(path).contains("ui")

    fun isFeature(path: String): Boolean = path.startsWith(":features:")

    fun isDesignSystem(path: String): Boolean = path.startsWith(":designsystem")

    fun isCore(path: String): Boolean = path.startsWith(":core")

    /** Modules whose sources stay free of UI and presentation frameworks. */
    fun isUniversal(path: String): Boolean =
        isApi(path) || isImpl(path) || isCore(path) || path.startsWith(":di:")

    fun isForbiddenUiGroup(group: String): Boolean =
        forbiddenUiGroups.any { group == it || group.startsWith("$it.") }

    /** Runtime and compile graphs of JVM, Android and Kotlin/Native targets. */
    fun isRelevantClasspathConfiguration(name: String, canBeResolved: Boolean): Boolean =
        canBeResolved && (
            name.endsWith("RuntimeClasspath") ||
                name.endsWith("CompileClasspath") ||
                name.endsWith("CompileKlibraries")
            )

    fun projectDependencyViolation(fromPath: String, toPath: String): String? = when {
        isApi(fromPath) &&
            (isImpl(toPath) || isPresentation(toPath) || isUi(toPath) || isDesignSystem(toPath)) ->
            "api modules depend only on api, core and di contracts"

        isImpl(fromPath) && (isPresentation(toPath) || isUi(toPath) || isDesignSystem(toPath)) ->
            "impl must not depend on presentation, ui or designsystem"

        isImpl(fromPath) && isFeature(toPath) && !isApi(toPath) ->
            "features interact only through api modules"

        isPresentation(fromPath) && isUi(toPath) ->
            "presentation must not depend on ui"

        isPresentation(fromPath) && isPresentation(toPath) &&
            pathSegments(fromPath).last() != pathSegments(toPath).last() ->
            "presentation adapters must not depend on each other"

        isUi(fromPath) && isImpl(toPath) ->
            "ui must not depend on impl"

        isUi(fromPath) && isPresentation(toPath) && fromPath != ":shell:ui:compose" ->
            "ui is presentation-framework independent; only shell:ui:compose may bind components"

        isCore(fromPath) && isFeature(toPath) ->
            "core must not depend on features"

        isDesignSystem(fromPath) && isFeature(toPath) ->
            "designsystem must not depend on features"

        else -> null
    }

    fun moduleDependencyViolation(fromPath: String, group: String): String? = when {
        isImpl(fromPath) && isForbiddenUiGroup(group) ->
            "impl must not depend on UI or presentation frameworks"

        isApi(fromPath) && isForbiddenUiGroup(group) ->
            "api contracts must not depend on UI or presentation frameworks"

        else -> null
    }

    fun forbiddenImport(modulePath: String, importPath: String): String? {
        if (!isUniversal(modulePath)) {
            return null
        }
        val forbidden = forbiddenImports.any { prefix ->
            importPath == prefix || importPath.startsWith("$prefix.")
        }
        return if (forbidden) {
            "universal modules must not import UI frameworks: $importPath"
        } else {
            null
        }
    }

    fun namingViolation(modulePath: String, declarationName: String): String? {
        if (!isFeature(modulePath)) {
            return null
        }
        if (declarationName.endsWith("Controller") || declarationName.endsWith("ViewModel")) {
            return "features must name the state holder <Feature>Store, not $declarationName"
        }
        val featureName = pathSegments(modulePath).getOrNull(1) ?: return null
        val expectedPrefix = featureName.replaceFirstChar { it.uppercaseChar() }
        val isStoreType = declarationName.endsWith("Store") ||
            declarationName.endsWith("StoreFactory")
        return when {
            isStoreType && isApi(modulePath) && !declarationName.startsWith(expectedPrefix) ->
                "api Store contract must start with $expectedPrefix: $declarationName"

            isStoreType && isImpl(modulePath) && !declarationName.startsWith("Default") ->
                "impl Store implementation must start with Default: $declarationName"

            else -> null
        }
    }
}
