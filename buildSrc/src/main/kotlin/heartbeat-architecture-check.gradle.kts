import heartbeat.ArchitectureRules
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

// Enforces the rules from docs/specs/architecture.md ("Запрещённые зависимости"):
// declared project and module dependencies, resolved compile/runtime graphs of the
// universal `impl` modules (JVM, Android and Kotlin/Native), and source-level checks
// for forbidden imports. The naming standard is checked by the Konsist tests in
// `:architecture-tests`, which this task runs. The task runs as part of `build` and `check`.

val violations = sortedSetOf<String>()

private val importPattern = Regex("""(?m)^\s*import\s+([\w.]+)""")

val checkTask = tasks.register("architectureCheck") {
    group = "verification"
    description = "Checks module dependencies, classpaths and imports against the architecture rules."
    doLast {
        violations.clear()
        rootProject.subprojects.forEach(::checkProject)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture violations found:\n" + violations.joinToString("\n"),
            )
        }
    }
}

// The Konsist source-level tests (contracts, package roles, intra-module layer direction) run
// before the dependency/classpath checks. Both must pass for `gradlew build`/`check` to succeed.
checkTask.configure {
    dependsOn(":architecture-tests:test")
}

// `gradlew build` runs the `build` task of every project that has it. The root project
// applies `base`, so its `build` and `check` tasks always exist; wiring the check to the
// root lifecycle tasks makes the gate part of a plain `gradlew build`.
tasks.matching { it.name == "build" || it.name == "check" }.configureEach {
    dependsOn(checkTask)
}

// A check scoped to one module (`gradlew :features:editor:impl:check`) runs the gate too.
subprojects.forEach { subproject ->
    subproject.tasks.matching { it.name == "check" }.configureEach {
        dependsOn(checkTask)
    }
}

private fun checkProject(project: Project) {
    project.configurations.forEach { configuration ->
        configuration.dependencies.filterIsInstance<ProjectDependency>().forEach { dependency ->
            ArchitectureRules.projectDependencyViolation(project.path, dependency.path)?.let { rule ->
                report(project.path, dependency.path, rule)
            }
        }
        configuration.dependencies.filterIsInstance<ModuleDependency>().forEach { dependency ->
            val group = dependency.group.orEmpty()
            ArchitectureRules.moduleDependencyViolation(project.path, group)?.let { rule ->
                report(project.path, "$group:${dependency.name}", rule)
            }
        }
    }
    checkResolvedDependencies(project)
    checkSources(project)
}

private fun checkResolvedDependencies(project: Project) {
    if (!ArchitectureRules.isImpl(project.path)) {
        return
    }
    project.configurations
        .filter {
            ArchitectureRules.isRelevantClasspathConfiguration(it.name, it.isCanBeResolved)
        }
        .forEach { configuration ->
            val result = try {
                configuration.incoming.resolutionResult
            } catch (error: Exception) {
                // A configuration that cannot be resolved must fail the gate: silently
                // skipping it would hide forbidden transitive dependencies.
                report(
                    project.path,
                    configuration.name,
                    "architecture check cannot resolve the configuration: ${error.message}",
                )
                return@forEach
            }
            result.allComponents.forEach { component ->
                val id = component.id
                if (id is ModuleComponentIdentifier) {
                    ArchitectureRules.moduleDependencyViolation(project.path, id.group)?.let { rule ->
                        report(
                            project.path,
                            "${id.group}:${id.module}:${id.version}",
                            "transitively $rule",
                        )
                    }
                }
            }
        }
}

private fun checkSources(project: Project) {
    project.fileTree("src") { include("**/*.kt") }.forEach { file ->
        val text = file.readText()
        val relativePath = file.relativeTo(project.projectDir).path
        importPattern.findAll(text).forEach { match ->
            ArchitectureRules.forbiddenImport(project.path, match.groupValues[1])?.let { rule ->
                report(project.path, relativePath, rule)
            }
        }
    }
}

private fun report(from: String, to: String, rule: String) {
    violations += "$from -> $to violates rule: $rule"
}
