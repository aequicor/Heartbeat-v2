import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension

// Shared detekt setup for every module. Rule values live in detekt.yml at the repository root
// and the rule sets are added as `detektPlugins` dependencies, so a module never configures
// detekt itself. `detektAll` runs the analysis of every module; the pre-push hook and the
// static-analysis workflow call it.

val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

val detektRuleSets = listOf(
    "detekt-compose-rules",
    "kode-compose-rules",
    "faire-detekt-rules",
).map { alias ->
    catalog.findLibrary(alias).orElseThrow {
        GradleException("Add the `$alias` library alias to gradle/libs.versions.toml.")
    }.get()
}

subprojects {
    apply(plugin = "dev.detekt")

    dependencies {
        detektRuleSets.forEach { ruleSet -> add("detektPlugins", ruleSet) }
    }

    tasks.withType(Detekt::class.java).configureEach {
        // Generated sources are not edited sources: Compose resources write Res.kt and SQLDelight
        // writes query classes under build/generated, including on Windows path separators.
        exclude("**/build/generated/**")
        exclude("**/generated/sqldelight/**")
        exclude("**/sqldelight/**")
    }

    // KMP source-set tasks take the whole Kotlin source set, which includes SQLDelight generated
    // code. The task-level exclude patterns do not filter those source-set roots on Windows, so
    // rebuild the source tree explicitly after the Kotlin and detekt plugins configured it.
    afterEvaluate {
        tasks.withType(Detekt::class.java).configureEach {
            setSource(
                source.filter { file ->
                    val path = file.invariantSeparatorsPath
                    !path.contains("/build/generated/") && !path.contains("/sqldelight/")
                },
            )
        }
    }

    extensions.getByType(DetektExtension::class.java).apply {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.layout.projectDirectory.file("detekt.yml"))
    }
}

val detektAll = tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt analysis in every module."
}

// Analysis tasks cover the plain `detekt` task, Android variant tasks and one
// `detekt<SourceSet>SourceSet` task per Kotlin Multiplatform source set; the latter are the only
// tasks that see sources of a KMP module. Config generation and baseline tasks have other types
// and stay out of the gate.
subprojects.forEach { subproject ->
    val analysisTasks = subproject.tasks.withType(Detekt::class.java)
    detektAll.configure { dependsOn(analysisTasks) }
}
