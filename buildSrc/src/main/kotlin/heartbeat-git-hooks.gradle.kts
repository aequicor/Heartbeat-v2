import org.gradle.api.tasks.Exec

// Enables the committed Git hooks. `.githooks/pre-push` runs `detektAll`, so a clone that ran
// this task blocks a push when detekt finds a violation.

tasks.register<Exec>("installGitHooks") {
    group = "verification"
    description = "Points Git at the committed .githooks directory."
    commandLine("git", "config", "core.hooksPath", ".githooks")
}
