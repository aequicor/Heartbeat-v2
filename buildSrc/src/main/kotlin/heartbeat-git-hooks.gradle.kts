import org.gradle.api.tasks.Exec

// Enables the committed Git hooks. The pre-push hook runs the review gates supported by the host.

tasks.register<Exec>("installGitHooks") {
    group = "verification"
    description = "Points Git at the committed .githooks directory."
    commandLine("git", "config", "core.hooksPath", ".githooks")
}
