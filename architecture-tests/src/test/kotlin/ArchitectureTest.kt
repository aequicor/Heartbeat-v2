import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Konsist architecture tests for the feature-first structure from
 * `docs/specs/architecture.md`.
 *
 * They read production sources of every feature module. Module-level dependency
 * direction and framework bans stay in the `heartbeat-architecture-check` Gradle
 * gate; these tests add the source-level checks that Gradle cannot express:
 * contract/implementation matching and intra-module layer direction.
 */
class ArchitectureTest {

    private val rootPackage = "io.github.aequicor.heartbeat"

    private val featurePrefix = "$rootPackage.features"

    /** Production sources of all feature modules; the test runs with workingDir = repo root. */
    private val scope = Konsist.scopeFromProduction(moduleName = ":features..").also {
        require(it.files.isNotEmpty()) { "No production Kotlin files found in features" }
    }

    /** Matches `...features.<any feature>.<role>` and its subpackages. */
    private fun rolePattern(role: String): String = "$featurePrefix..$role.."

    @Test
    fun defaultImplementationImplementsSameNamedContract() {
        scope.classesAndObjects(includeNested = false, includeLocal = false)
            .filter { it.name.startsWith("Default") }
            .assertTrue { implementation ->
                implementation.parentInterfaces(indirectParents = true).any { parent ->
                    parent.sourceDeclaration
                        ?.asInterfaceDeclaration()
                        ?.name == implementation.name.removePrefix("Default")
                }
            }
    }

    @Test
    fun featureLayersFollowImportDirection() {
        scope.assertArchitecture {
            val api = Layer("API", rolePattern("api"))
            val domain = Layer("Domain", rolePattern("domain"))
            val data = Layer("Data", rolePattern("data"))
            val presentation = Layer("Presentation", rolePattern("presentation"))
            val di = Layer("DI", rolePattern("di"))
            val component = Layer("Component", rolePattern("component"))
            val ui = Layer("UI", rolePattern("ui"))

            api.dependsOnNothing()
            domain.dependsOnNothing()
            data.dependsOn(domain)
            presentation.dependsOn(domain, api)
            di.dependsOn(api, domain, presentation)
            component.dependsOn(api)
            ui.dependsOn(api)
        }
    }
}
