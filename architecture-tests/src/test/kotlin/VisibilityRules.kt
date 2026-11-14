/**
 * Visibility of feature implementation details.
 *
 * A declaration in a feature `impl` or adapter module is a detail, so it must be `internal`
 * unless the DI boundary or a public contract needs it: Metro builds injected types and
 * contributed bindings in the module that declares them, and the final graph lives in the
 * platform compilation (HB-ARCH-014), so those declarations stay `public`.
 *
 * The allowlist works by role, not by package: a public declaration is admitted only when its
 * role defines a contract — a `*Repository` interface, a `data`/`sealed`/`enum`/`value` domain
 * model, a `<Feature>Component` or `<Feature>ComponentFactory` interface, or an
 * `@Composable *Screen` function. Metro annotations are the only other exception, and only in the
 * roles whose types the final graph constructs or binds. Every other public declaration in a
 * feature module is an implementation detail and must be `internal`.
 *
 * This rule minimises the internal API; it does not replace the module boundary. The dependency
 * graph in `architectureCheck` forbids dependencies between feature implementations. AI Studio
 * adapters may use the listed contracts in their own `impl`. The Store's mutable state and
 * internal state model stay within that module.
 */
internal object VisibilityRules {

    /** Annotations that put a declaration on the Metro graph, so another module must reach it. */
    private val metroAnnotations = setOf(
        "Inject",
        "ContributesBinding",
        "ContributesTo",
        "ContributesIntoSet",
        "ContributesIntoMap",
        "Provides",
        "Binds",
        "SingleIn",
        "DependencyGraph",
    )

    /** Roles whose declarations the final Metro graph constructs or binds. */
    private val metroRoles = setOf(
        listOf("domain", "usecase"),
        listOf("data", "datasource"),
        listOf("data", "repository"),
        listOf("presentation"),
        listOf("di"),
        listOf("component"),
        listOf("ui"),
    )

    /** Modifiers of a value declaration that carries domain meaning. */
    private val modelModifiers = setOf("DATA", "SEALED", "ENUM", "VALUE")

    private val aiStudioAdapterContracts = setOf(
        "AiStudioAction",
        "AiStudioScreenState",
        "AiStudioSettings",
        "AiStudioStore",
        "AiStudioStoreFactory",
    )

    /**
     * Returns the violation message, or `null` when the declaration may stay public.
     */
    fun violation(
        packageName: String,
        declarationName: String,
        isPublic: Boolean = true,
        declarationKind: DeclarationKind = DeclarationKind.CLASS,
        annotations: List<String> = listOf(),
        modifiers: List<String> = listOf(),
        projectPath: String? = null,
    ): String? {
        val role = if (isPublic) NamingRules.featurePath(packageName)?.drop(1) else null
        return when {
            role == null -> null
            // The api role is the feature's public surface, checked by `apiContractsArePublic`.
            NamingRules.isApiPackage(packageName) -> null
            packageName == "io.github.aequicor.heartbeat.features.aistudio.presentation.contract" &&
                declarationName in aiStudioAdapterContracts -> null
            role in metroRoles && metroAnnotations.any(annotations::contains) -> null
            role == listOf("domain", "repository") ->
                repositoryContractViolation(declarationKind, declarationName)

            role == listOf("data", "datasource") ->
                dataSourceContractViolation(declarationKind, declarationName)

            role == listOf("domain", "model") -> modelViolation(
                declarationKind,
                declarationName,
                modifiers,
            )

            role == listOf("component") -> componentContractViolation(
                featureName(packageName, projectPath),
                declarationKind,
                declarationName,
            )

            role == listOf("ui") -> screenViolation(declarationKind, declarationName, annotations)
            else -> detailViolation(declarationName)
        }
    }

    /**
     * The `api` role and its subpackages make up the feature's public surface, so every
     * declaration there must be explicitly `public` (HB-ARCH-040). [violation] skips the role
     * because its contracts are public by default; this check closes the subpackages.
     */
    fun apiContractViolation(
        packageName: String,
        declarationName: String,
        isPublic: Boolean,
    ): String? = when {
        !NamingRules.isApiPackage(packageName) -> null
        isPublic -> null
        else -> "api contract must be public: $declarationName"
    }

    private fun repositoryContractViolation(
        declarationKind: DeclarationKind,
        declarationName: String,
    ): String? = if (
        declarationKind == DeclarationKind.INTERFACE &&
        declarationName.endsWith("Repository")
    ) {
        null
    } else {
        "repository contract must be an interface ending with Repository: $declarationName"
    }

    /**
     * A data source is the storage boundary of a feature. Its contract is an interface so the
     * implementation (for example SQLDelight) stays replaceable; the class that implements it is a
     * Metro binding and stays public through the Metro annotation exception above.
     */
    private fun dataSourceContractViolation(
        declarationKind: DeclarationKind,
        declarationName: String,
    ): String? = if (
        declarationKind == DeclarationKind.INTERFACE &&
        declarationName.endsWith("DataSource")
    ) {
        null
    } else {
        "data source contract must be an interface ending with DataSource: $declarationName"
    }

    private fun modelViolation(
        declarationKind: DeclarationKind,
        declarationName: String,
        modifiers: List<String>,
    ): String? = if (
        declarationKind in setOf(DeclarationKind.CLASS, DeclarationKind.INTERFACE) &&
        modifiers.any(modelModifiers::contains)
    ) {
        null
    } else {
        "domain model must be a data, sealed, enum or value declaration: $declarationName"
    }

    private fun componentContractViolation(
        feature: String,
        declarationKind: DeclarationKind,
        declarationName: String,
    ): String? {
        val allowed = setOf("${feature}Component", "${feature}ComponentFactory")
        return if (declarationKind == DeclarationKind.INTERFACE && declarationName in allowed) {
            null
        } else {
            "component contract must be ${feature}Component or ${feature}ComponentFactory: " +
                declarationName
        }
    }

    private fun screenViolation(
        declarationKind: DeclarationKind,
        declarationName: String,
        annotations: List<String>,
    ): String? = if (
        declarationKind == DeclarationKind.FUNCTION &&
        "Composable" in annotations &&
        declarationName.endsWith("Screen")
    ) {
        null
    } else {
        "screen must be a @Composable function ending with Screen: $declarationName"
    }

    private fun detailViolation(declarationName: String): String =
        "implementation detail must be internal: $declarationName"

    /** Capitalised feature name: `editor` becomes `Editor`, `ai-studio` becomes `AiStudio`. */
    private fun featureName(packageName: String, projectPath: String?): String {
        val segment = NamingRules.featurePath(packageName)!!.first()
        return NamingRules.featureName(segment, projectPath)
    }
}
