import org.gradle.api.GradleException
import org.gradle.api.Project

// Shortcuts that build and start the desktop entry point of one OS.
// `runDev` selects the entry point of the current host; `runDev<Os>` names the
// entry point explicitly and fails on another host. Documentation: docs/setup.md.

run {
    DevRuns(project).register()
}

/**
 * Desktop entry point of one operating system.
 *
 * @property taskName name of the `runDev<Os>` task.
 * @property osPrefix prefix of the `os.name` value that identifies the host.
 * @property archs `os.arch` values that can run the target.
 * @property module Gradle path of the platform module.
 * @property runTask task that builds and runs the debug executable of the module.
 * @property label operating system name for descriptions and error messages.
 */
data class EntryPoint(
    val taskName: String,
    val osPrefix: String,
    val archs: Set<String>,
    val module: String,
    val runTask: String,
    val label: String,
)

/**
 * Registers the `runDev` and `runDev<Os>` tasks.
 *
 * `runDev` selects the entry point of the current host. `runDev<Os>` names one
 * entry point and fails on another host with a hint.
 *
 * @param project project that receives the tasks.
 */
class DevRuns(private val project: Project) {

    private val hostOs: String = project.providers.systemProperty("os.name")
        .get().lowercase()
    private val hostArch: String = project.providers.systemProperty("os.arch")
        .get().lowercase()

    private val entryPoints = listOf(
        EntryPoint(
            taskName = "runDevWindows",
            osPrefix = "windows",
            archs = setOf("amd64", "x86_64"),
            module = ":platform:windows",
            runTask = "runDebugExecutableMingwX64",
            label = "Windows x64",
        ),
        EntryPoint(
            taskName = "runDevLinux",
            osPrefix = "linux",
            archs = setOf("amd64", "x86_64"),
            module = ":platform:linux",
            runTask = "runDebugExecutableLinuxX64",
            label = "Linux x64",
        ),
        EntryPoint(
            taskName = "runDevMacos",
            osPrefix = "mac",
            archs = setOf("aarch64"),
            module = ":platform:macos",
            runTask = "runDebugExecutableMacosArm64",
            label = "macOS ARM64",
        ),
    )

    fun register() {
        entryPoints.forEach(::registerEntryPoint)
        registerRunDev()
    }

    private fun registerEntryPoint(entry: EntryPoint) {
        project.tasks.register(entry.taskName) {
            group = "dev"
            description = "Build and run the desktop app for ${entry.label}"
            if (matchesHost(entry)) {
                dependsOn("${entry.module}:${entry.runTask}")
            } else {
                doFirst {
                    throw GradleException(
                        "${entry.taskName} runs only on ${entry.label}. " +
                            "Current host is $hostOs/$hostArch. Use runDev on a supported host.",
                    )
                }
            }
        }
    }

    private fun registerRunDev() {
        project.tasks.register("runDev") {
            group = "dev"
            description = "Build and run the desktop app for the current OS"
            val current = entryPoints.firstOrNull(::matchesHost)
            if (current != null) {
                dependsOn(current.taskName)
            } else {
                doFirst {
                    throw GradleException(
                        "No desktop entry point for $hostOs/$hostArch. " +
                            "Supported hosts: ${entryPoints.joinToString { it.label }}.",
                    )
                }
            }
        }
    }

    /**
     * Checks whether [entry] targets the current host.
     *
     * @param entry entry point to check.
     * @return `true` when the host OS name and architecture match [entry].
     */
    private fun matchesHost(entry: EntryPoint): Boolean =
        hostOs.startsWith(entry.osPrefix) && hostArch in entry.archs
}
