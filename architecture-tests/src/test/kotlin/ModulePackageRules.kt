/**
 * Module placement rule from `docs/specs/architecture.md` ("Имена архитектурных ролей"):
 * the Gradle module that holds a production source decides which package it may declare.
 *
 * Without this rule a file can sit in `features/editor/impl` but declare a package outside the
 * feature and escape the naming and visibility rules, because those derive the role from the
 * declaration only. The module is derived from the source path, and every mapping below is
 * explicit: an unknown module role is a violation, not a silent pass.
 *
 * The rule is pure so [ModulePackageRulesTest] covers every mapping directly; the Konsist test
 * extracts the path and the package from the PSI.
 */
internal object ModulePackageRules {

    private const val ROOT_PACKAGE = "io.github.aequicor.heartbeat"

    private const val FEATURE_ROOT = "$ROOT_PACKAGE.features"

    private const val DESIGN_SYSTEM_ROOT = "$ROOT_PACKAGE.designsystem"

    private const val CORE_ROOT = "$ROOT_PACKAGE.core"

    private const val DI_ROOT = "$ROOT_PACKAGE.di"

    private const val SHELL_ROOT = "$ROOT_PACKAGE.shell"

    private const val NAVIGATION_ROOT = "$ROOT_PACKAGE.navigation"

    private const val STORAGE_ROOT = "$ROOT_PACKAGE.storage"

    /** Repository areas that own a module tree: the first segment of the module path. */
    private val moduleAreas =
        listOf("features", "designsystem", "core", "di", "shell", "navigation", "storage", "platform")

    /** Roles of the universal implementation module: one Gradle module holds all of them. */
    private val implRoles = listOf("domain", "data", "presentation", "di")

    /** Package role of a Presentation adapter module (`presentation/decompose`, ...). */
    private const val COMPONENT_ROLE = "component"

    /** Package role of a platform UI module (`ui/compose`, ...). */
    private const val UI_ROLE = "ui"

    /** Returns the violation message, or `null` when the package fits the module. */
    fun violation(projectPath: String, packageName: String): String? {
        val module = moduleSegments(projectPath) ?: return null
        return when (module.first()) {
            "features" -> featureViolation(module, packageName)
            "designsystem" -> designSystemViolation(module, packageName)
            "core" -> coreViolation(module, packageName)
            "di" -> diViolation(module, packageName)
            "shell" -> shellViolation(module, packageName)
            "navigation" -> navigationViolation(module, packageName)
            "storage" -> storageViolation(module, packageName)
            "platform" -> platformViolation(module, packageName)
            else -> null
        }
    }

    private fun featureViolation(module: List<String>, packageName: String): String? {
        val feature = module[1]
        // A Gradle directory can use a hyphen, while a Kotlin package cannot: the feature
        // `features/ai-studio` declares the package segment `aistudio`.
        val packageFeature = feature.replace("-", "")
        val root = "$FEATURE_ROOT.$packageFeature"
        val modulePath = module.joinToString("/")
        val rest = module.drop(2)
        val allowed = when (rest) {
            listOf("api") -> listOf("$root.api")
            // Roles only: the feature root itself is not a package of the implementation module,
            // otherwise every subpackage, including `api`, would match the root prefix.
            listOf("impl") -> implRoles.map { "$root.$it" }
            else -> when {
                rest.size == 2 && rest[0] == "presentation" -> listOf("$root.$COMPONENT_ROLE")
                rest.size == 2 && rest[0] == "ui" -> listOf("$root.$UI_ROLE")
                else -> return "unknown feature module role: $modulePath"
            }
        }
        return mismatch(modulePath, packageName, allowed)
    }

    private fun designSystemViolation(module: List<String>, packageName: String): String? =
        mismatch(
            modulePath = module.joinToString("/"),
            packageName = packageName,
            allowed = listOf("$DESIGN_SYSTEM_ROOT.${module.last()}"),
        )

    /** `core/platform-api` declares `...core.platformapi`: the module directory names the package. */
    private fun coreViolation(module: List<String>, packageName: String): String? {
        val modulePath = module.joinToString("/")
        if (module.size < 2) return "unknown core module: $modulePath"
        val suffix = module.drop(1).joinToString(".") { segment -> segment.replace("-", "") }
        return mismatch(modulePath, packageName, listOf("$CORE_ROOT.$suffix"))
    }

    /** `di/api` holds the shared Metro scopes in the `di` package. */
    private fun diViolation(module: List<String>, packageName: String): String? {
        val modulePath = module.joinToString("/")
        return if (module == listOf("di", "api")) {
            mismatch(modulePath, packageName, listOf(DI_ROOT))
        } else {
            "unknown di module role: $modulePath"
        }
    }

    /**
     * Shell modules keep one package area until their role packages get sources: the area check
     * still rejects a declaration from an unrelated package.
     */
    private fun shellViolation(module: List<String>, packageName: String): String? =
        mismatch(module.joinToString("/"), packageName, listOf(SHELL_ROOT))

    /** Navigation modules keep one package area per module: routes, Decompose SPI, Compose SPI. */
    private fun navigationViolation(module: List<String>, packageName: String): String? {
        val modulePath = module.joinToString("/")
        val role = when (module) {
            listOf("navigation", "api") -> "api"
            listOf("navigation", "decompose") -> "decompose"
            listOf("navigation", "decompose-compose") -> "compose"
            else -> return "unknown navigation module role: $modulePath"
        }
        return mismatch(modulePath, packageName, listOf("$NAVIGATION_ROOT.$role"))
    }

    /**
     * Platform modules are entry points: their sources live in the application root package or in
     * the `di` composition-root package, so a feature or design-system package declared inside a
     * platform module is reported instead of being accepted as a subpackage of the root.
     */
    private fun platformViolation(module: List<String>, packageName: String): String? =
        when {
            packageName == ROOT_PACKAGE -> null
            packageName == "$ROOT_PACKAGE.di" || packageName.startsWith("$ROOT_PACKAGE.di.") -> null
            else -> "package $packageName does not belong to module ${module.joinToString("/")}: " +
                "expected $ROOT_PACKAGE or $ROOT_PACKAGE.di"
        }

    /** `storage/database` declares `...storage.database`: storage owns composition, not feature roles. */
    private fun storageViolation(module: List<String>, packageName: String): String? {
        val modulePath = module.joinToString("/")
        val suffix = module.drop(1).joinToString(".") { segment -> segment.replace("-", "") }
        return mismatch(modulePath, packageName, listOf("$STORAGE_ROOT.$suffix"))
    }

    private fun mismatch(modulePath: String, packageName: String, allowed: List<String>): String? =
        if (allowed.any { packageName == it || packageName.startsWith("$it.") }) {
            null
        } else {
            "package $packageName does not belong to module $modulePath: expected " +
                allowed.joinToString(" or ")
        }

    /**
     * Segments of the Gradle module that holds the source: `features/editor/impl` for
     * `features/editor/impl/src/commonMain/kotlin/...`. Paths outside the architecture areas have
     * no placement rule.
     */
    private fun moduleSegments(projectPath: String): List<String>? {
        val modulePath = projectPath.replace('\\', '/')
            .substringBefore("/src/", missingDelimiterValue = "")
        if (modulePath.isEmpty()) return null
        val segments = modulePath.split("/")
        val start = segments.indexOfFirst { it in moduleAreas }
        return if (start >= 0) segments.drop(start) else null
    }
}
