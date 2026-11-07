import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency

// Enforces the module dependency rules from docs/specs/architecture.md
// ("Запрещённые зависимости"). The task walks declared dependencies of every
// subproject without resolving configurations, so it stays cheap. It runs as
// part of `build` and `check` and fails with the list of violations.

val violations = mutableListOf<String>()

val checkTask = tasks.register("architectureCheck") {
    group = "verification"
    description = "Checks module dependencies against the architecture rules."
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

tasks.matching { it.name == "build" || it.name == "check" }.configureEach {
    dependsOn(checkTask)
}

private fun checkProject(project: Project) {
    project.configurations.forEach { configuration ->
        configuration.dependencies.filterIsInstance<ProjectDependency>().forEach {
            checkProjectDependency(project, project.rootProject.project(it.path))
        }
        configuration.dependencies.filterIsInstance<ModuleDependency>().forEach {
            checkModuleDependency(project, it)
        }
    }
}

private fun checkProjectDependency(from: Project, to: Project) {
    val fromPath = from.path
    val toPath = to.path

    when {
        isApi(fromPath) -> {
            if (isImpl(toPath) || isPresentation(toPath) || isUi(toPath) || isDesignSystem(toPath)) {
                report(fromPath, toPath, "api modules depend only on api, core and di contracts")
            }
        }

        isImpl(fromPath) -> {
            if (isPresentation(toPath) || isUi(toPath) || isDesignSystem(toPath)) {
                report(fromPath, toPath, "impl must not depend on presentation, ui or designsystem")
            }
            if (isFeature(toPath) && !isApi(toPath)) {
                report(fromPath, toPath, "features interact only through api modules")
            }
        }

        isPresentation(fromPath) -> {
            if (isUi(toPath)) {
                report(fromPath, toPath, "presentation must not depend on ui")
            }
            if (isPresentation(toPath) &&
                pathSegments(fromPath).last() != pathSegments(toPath).last()
            ) {
                report(fromPath, toPath, "presentation adapters must not depend on each other")
            }
        }

        isUi(fromPath) -> {
            if (isImpl(toPath)) {
                report(fromPath, toPath, "ui must not depend on impl")
            }
            if (isPresentation(toPath) && fromPath != ":shell:ui:compose") {
                report(
                    fromPath,
                    toPath,
                    "ui is presentation-framework independent; only shell:ui:compose may bind components",
                )
            }
        }

        isCore(fromPath) -> {
            if (isFeature(toPath)) {
                report(fromPath, toPath, "core must not depend on features")
            }
        }

        isDesignSystem(fromPath) -> {
            if (isFeature(toPath)) {
                report(fromPath, toPath, "designsystem must not depend on features")
            }
        }
    }
}

private val forbiddenImplGroups = listOf(
    "com.arkivanov.decompose",
    "org.jetbrains.compose",
    "androidx.compose",
)

private fun checkModuleDependency(from: Project, dependency: ModuleDependency) {
    val fromPath = from.path
    val group = dependency.group.orEmpty()
    if (isImpl(fromPath) && forbiddenImplGroups.any { group.startsWith(it) }) {
        report(
            fromPath,
            "$group:${dependency.name}",
            "impl must not depend on UI or presentation frameworks",
        )
    }
}

private fun report(from: String, to: String, rule: String) {
    violations += "$from -> $to violates rule: $rule"
}

private fun pathSegments(path: String): List<String> =
    path.split(":").filter { it.isNotEmpty() }

private fun isApi(path: String): Boolean =
    path == ":shell:api" ||
        (path.startsWith(":features:") && pathSegments(path).last() == "api")

private fun isImpl(path: String): Boolean =
    path == ":shell:impl" ||
        (path.startsWith(":features:") && pathSegments(path).last() == "impl")

private fun isPresentation(path: String): Boolean =
    pathSegments(path).contains("presentation")

private fun isUi(path: String): Boolean =
    pathSegments(path).contains("ui")

private fun isFeature(path: String): Boolean = path.startsWith(":features:")

private fun isDesignSystem(path: String): Boolean = path.startsWith(":designsystem")

private fun isCore(path: String): Boolean = path.startsWith(":core")
