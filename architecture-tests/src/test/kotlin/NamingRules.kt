/**
 * Architecture Naming Convention from `docs/specs/architecture.md`
 * ("Соглашения по именованию"): a declaration gets its role from the package it
 * resides in, and the role fixes the name.
 *
 * The rules are pure so every convention is unit-tested directly; the Konsist
 * test only extracts package, kind, visibility and annotations from the PSI and
 * feeds them here. Only top-level declarations are checked: nested Action and
 * Effect members take the name of their intent, not of an architectural role.
 */
internal enum class DeclarationKind { CLASS, INTERFACE, OBJECT, FUNCTION, OTHER }

internal object NamingRules {

    private const val FEATURE_PACKAGE = "io.github.aequicor.heartbeat.features"

    private const val DESIGN_SYSTEM_PACKAGE = "io.github.aequicor.heartbeat.designsystem"

    /** State holders are Stores; these names belong to other presentation frameworks. */
    private val foreignStateHolderSuffixes = listOf("Controller", "ViewModel")

    /** Contracts that live in the feature `api` package. */
    private val contractSuffixes = listOf("StoreFactory", "Store", "State", "Action", "Effect")

    private val mapperFunction = Regex("""to[A-Z]\w*""")

    /**
     * Returns the violation message, or `null` when [declarationName] follows the
     * convention of [packageName]. Declarations outside the feature and Design
     * System packages have no naming rule.
     */
    fun violation(
        packageName: String,
        declarationKind: DeclarationKind,
        declarationName: String,
        isPublic: Boolean = true,
        annotations: List<String> = listOf(),
    ): String? = when {
        foreignStateHolderSuffixes.any(declarationName::endsWith) ->
            "features must name the state holder <Feature>Store, not $declarationName"

        packageName.startsWith("$DESIGN_SYSTEM_PACKAGE.") ->
            violationInDesignSystem(declarationName, isPublic)

        else -> featureViolation(packageName, declarationKind, declarationName, annotations)
    }

    private fun featureViolation(
        packageName: String,
        declarationKind: DeclarationKind,
        declarationName: String,
        annotations: List<String>,
    ): String? {
        val path = featurePackagePath(packageName) ?: return null
        val feature = path.first().replaceFirstChar { it.uppercaseChar() }
        return when (path.drop(1)) {
            listOf("api") -> apiContractViolation(feature, declarationName)

            listOf("domain", "usecase") -> suffixViolation(
                role = "use case",
                suffix = "UseCase",
                declarationName = declarationName,
                declarationKind = declarationKind,
                typesOnly = true,
            )

            listOf("domain", "repository") -> repositoryContractViolation(
                declarationName,
                declarationKind,
            )

            listOf("data", "datasource") -> dataSourceViolation(declarationName, declarationKind)

            listOf("data", "dto") -> suffixViolation(
                role = "DTO",
                suffix = "Dto",
                declarationName = declarationName,
                declarationKind = declarationKind,
            )

            listOf("data", "mapper") -> mapperViolation(declarationName, declarationKind)

            listOf("data", "repository") -> suffixViolation(
                role = "repository implementation",
                suffix = "Repository",
                declarationName = declarationName,
                declarationKind = declarationKind,
                typesOnly = true,
            )

            listOf("presentation") -> presentationViolation(feature, declarationName, declarationKind)

            listOf("di") -> metroBindingViolation(declarationKind, declarationName)

            listOf("component") -> componentViolation(feature, declarationName, declarationKind)

            listOf("ui") -> screenViolation(declarationName, declarationKind, annotations)

            else -> null
        }
    }

    private fun metroBindingViolation(
        declarationKind: DeclarationKind,
        declarationName: String,
    ): String? = when {
        declarationKind.isType() && !declarationName.startsWith("Default") ->
            "Metro binding must start with Default: $declarationName"

        else -> null
    }

    private fun violationInDesignSystem(declarationName: String, isPublic: Boolean): String? =
        if (isPublic && !declarationName.startsWith("Hb")) {
            "design system public API must start with Hb: $declarationName"
        } else {
            null
        }

    private fun apiContractViolation(feature: String, declarationName: String): String? = when {
        !declarationName.startsWith(feature) ->
            "api contract must start with $feature: $declarationName"

        contractSuffixes.none(declarationName::endsWith) ->
            "api contract must be <Feature>Store, <Feature>StoreFactory, <Feature>State, " +
                "<Feature>Action or <Feature>Effect: $declarationName"

        else -> null
    }

    private fun repositoryContractViolation(
        declarationName: String,
        declarationKind: DeclarationKind,
    ): String? = when {
        !declarationKind.isType() -> null
        declarationName.startsWith("Default") ->
            "repository implementation belongs to data.repository: $declarationName"

        !declarationName.endsWith("Repository") ->
            "repository contract must end with Repository: $declarationName"

        else -> null
    }

    private fun dataSourceViolation(
        declarationName: String,
        declarationKind: DeclarationKind,
    ): String? = when {
        !declarationKind.isType() -> null
        declarationName.endsWith("DataSource") || declarationName.endsWith("Dto") -> null
        else -> "data source must end with DataSource and its DTO with Dto: $declarationName"
    }

    private fun mapperViolation(
        declarationName: String,
        declarationKind: DeclarationKind,
    ): String? = when {
        declarationKind == DeclarationKind.FUNCTION -> mapperFunctionViolation(declarationName)

        declarationKind.isType() && !declarationName.endsWith("Mapper") ->
            "mapper class must end with Mapper: $declarationName"

        else -> null
    }

    private fun presentationViolation(
        feature: String,
        declarationName: String,
        declarationKind: DeclarationKind,
    ): String? = when {
        declarationKind == DeclarationKind.FUNCTION -> mapperFunctionViolation(declarationName)

        declarationKind.isType() && declarationName != "Default${feature}Store" ->
            "store implementation must be named Default${feature}Store: $declarationName"

        else -> null
    }

    private fun mapperFunctionViolation(declarationName: String): String? =
        if (mapperFunction.matches(declarationName)) {
            null
        } else {
            "mapper function must be named to<Type>: $declarationName"
        }

    private fun componentViolation(
        feature: String,
        declarationName: String,
        declarationKind: DeclarationKind,
    ): String? {
        if (!declarationKind.isType()) {
            return null
        }
        val allowed = setOf(
            "${feature}Component",
            "${feature}ComponentFactory",
            "Default${feature}Component",
            "Default${feature}ComponentFactory",
        )
        return if (declarationName in allowed) {
            null
        } else {
            "component must be <Feature>Component or Default<Feature>Component: $declarationName"
        }
    }

    private fun screenViolation(
        declarationName: String,
        declarationKind: DeclarationKind,
        annotations: List<String>,
    ): String? = when {
        declarationKind != DeclarationKind.FUNCTION -> null
        "Composable" !in annotations -> null
        !declarationName.endsWith("Screen") -> "screen composable must end with Screen: $declarationName"
        else -> null
    }

    private fun suffixViolation(
        role: String,
        suffix: String,
        declarationName: String,
        declarationKind: DeclarationKind,
        typesOnly: Boolean = false,
    ): String? = when {
        typesOnly && !declarationKind.isType() -> null
        !declarationName.endsWith(suffix) -> "$role must end with $suffix: $declarationName"
        else -> null
    }

    /** Package segments after `...heartbeat.features.<feature>`; `null` outside a feature. */
    private fun featurePackagePath(packageName: String): List<String>? {
        val prefix = "$FEATURE_PACKAGE."
        if (!packageName.startsWith(prefix)) {
            return null
        }
        val segments = packageName.removePrefix(prefix).split(".")
        return if (segments.size < 2) null else segments
    }

    private fun DeclarationKind.isType(): Boolean = this == DeclarationKind.CLASS ||
        this == DeclarationKind.INTERFACE ||
        this == DeclarationKind.OBJECT
}
