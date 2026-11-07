/**
 * Visibility of feature implementation details.
 *
 * A declaration in a feature `impl` or adapter module is a detail, so it must be `internal`
 * unless the DI boundary or a public contract needs it: Metro builds injected types and
 * contributed bindings in the module that declares them, and the final graph lives in the
 * platform compilation (HB-ARCH-014), so those declarations stay `public`. The audit is an
 * allowlist instead of a blocklist (HB-ARCH-040): only the roles that carry contracts or the
 * DI boundary may be public, every other declaration in a feature module must be `internal`
 * or `private`.
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

    /**
     * Roles whose declarations are public contracts or the DI boundary. The `api` role and its
     * subpackages are contracts checked separately by `apiContractsArePublic`, so they are
     * skipped here as well.
     */
    private val publicContractRoles = setOf(
        listOf("domain", "repository"),
        listOf("domain", "model"),
        listOf("di"),
        listOf("ui"),
    )

    /**
     * Returns the violation message, or `null` when the declaration may stay public.
     */
    fun violation(
        packageName: String,
        declarationName: String,
        isPublic: Boolean,
        annotations: List<String>,
    ): String? {
        val role = NamingRules.featurePath(packageName)?.drop(1)
        return when {
            !isPublic || role == null -> null
            metroAnnotations.any(annotations::contains) -> null
            NamingRules.isApiPackage(packageName) -> null
            role in publicContractRoles -> null
            role == listOf("component") && !declarationName.startsWith("Default") -> null
            else -> "implementation detail must be internal: $declarationName"
        }
    }

    /**
     * The `api` role and its subpackages make up the feature's public surface, so every
     * declaration there must be explicitly `public` (HB-ARCH-040). [violation] skips the role
     * because its contracts are public by default; this check closes the subpackages, which
     * `endsWith(".api")` would miss.
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
}
