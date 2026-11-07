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

    extensions.getByType(DetektExtension::class.java).apply {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.layout.projectDirectory.file("detekt.yml"))
    }
}

tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt analysis in every module."
    dependsOn(subprojects.map { "${it.path}:detekt" })
}
