/**
 * Module placement rule from `docs/specs/architecture.md` ("Имена архитектурных ролей"):
 * the Gradle module that holds a production source decides which package it may declare.
 *
 * Without this rule a file can sit in `features/editor/impl` but declare a package outside
 * the feature and escape the naming and visibility rules, because those derive the role from
 * the declaration only. The module is derived from the source path, and every mapping below is
 * explicit: an unknown module role is a violation, not a silent pass.
 *
 * The rule is pure so [ModulePackageRulesTest] covers every mapping directly; the Konsist test
 * extracts the path and the package from the PSI.
 */
internal object ModulePackageRules {

    private const val FEATURE_ROOT = "io.github.aequicor.heartbeat.features"

    private const val DESIGN_SYSTEM_ROOT = "io.github.aequicor.heartbeat.designsystem"

    /** Roles of the universal implementation module: one Gradle module holds all of them. */
    private val implRoles = listOf("domain", "data", "presentation", "di")

    /** Package role of a Presentation adapter module (`presentation/decompose`, ...). */
    private const val COMPONENT_ROLE = "component"

    /** Package role of a platform UI module (`ui/compose`, ...). */
    private const val UI_ROLE = "ui"

    /** Returns the violation message, or `null` when the package fits the module. */
    fun violation(projectPath: String, packageName: String): String? {
        val module = moduleSegments(projectPath) ?: return null
        return when {
            module.size >= 3 && module.first() == "features" -> featureViolation(module, packageName)
            module.size >= 2 && module.first() == "designsystem" ->
                designSystemViolation(module, packageName)

            else -> null
        }
    }

    private fun featureViolation(module: List<String>, packageName: String): String? {
        val feature = module[1]
        val root = "$FEATURE_ROOT.$feature"
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

    private fun mismatch(modulePath: String, packageName: String, allowed: List<String>): String? =
        if (allowed.any { packageName == it || packageName.startsWith("$it.") }) {
            null
        } else {
            "package $packageName does not belong to module $modulePath: expected " +
                allowed.joinToString(" or ")
        }

    /**
     * Segments of the Gradle module that holds the source: `features/editor/impl` for
     * `features/editor/impl/src/commonMain/kotlin/...`. Paths outside `features` and
     * `designsystem` have no placement rule.
     */
    private fun moduleSegments(projectPath: String): List<String>? {
        val modulePath = projectPath.replace('\\', '/').substringBefore("/src/", missingDelimiterValue = "")
        if (modulePath.isEmpty()) return null
        val segments = modulePath.split("/")
        val start = segments.indexOfFirst { it == "features" || it == "designsystem" }
        return if (start >= 0) segments.drop(start) else null
    }
}
