/**
 * Visibility of feature implementation details.
 *
 * A declaration in a feature `impl` or adapter module is a detail, so it must be `internal`
 * unless the DI boundary needs it: Metro builds injected types and contributed bindings in the
 * module that declares them, and the final graph lives in the platform compilation (HB-ARCH-014),
 * so those declarations stay `public`. The audit is explicit about the exceptions instead of
 * forcing every implementation to `internal`.
 */
internal object VisibilityRules {

    /** Annotations that put a declaration on the Metro graph, so another module must reach it. */
    private val metroAnnotations = setOf(
        "Inject",
        "ContributesBinding",
        "ContributesTo",
        "Provides",
        "Binds",
        "SingleIn",
        "DependencyGraph",
    )

    private val dtoPackages = listOf(
        listOf("data", "datasource"),
        listOf("data", "dto"),
    )

    private val mapperPackages = listOf(
        listOf("data", "mapper"),
        listOf("presentation"),
    )

    /**
     * Returns the violation message, or `null` when the declaration may stay public.
     */
    fun violation(
        packageName: String,
        declarationKind: DeclarationKind,
        declarationName: String,
        isPublic: Boolean,
        annotations: List<String>,
    ): String? {
        val role = NamingRules.featurePath(packageName)?.drop(1)
        return when {
            !isPublic || role == null -> null
            metroAnnotations.any(annotations::contains) -> null
            role in dtoPackages && declarationName.endsWith("Dto") ->
                "DTO must be internal: $declarationName"

            role in mapperPackages && declarationKind == DeclarationKind.FUNCTION ->
                "mapper function must be internal: $declarationName"

            role == listOf("presentation") && declarationName.startsWith("Default") ->
                "store implementation must be internal: $declarationName"

            role == listOf("component") && declarationName.startsWith("Default") ->
                "component implementation must be internal: $declarationName"

            else -> null
        }
    }
}
