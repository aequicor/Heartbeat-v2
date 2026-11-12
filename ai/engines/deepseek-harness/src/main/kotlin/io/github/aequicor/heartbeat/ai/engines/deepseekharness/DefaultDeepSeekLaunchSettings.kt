package io.github.aequicor.heartbeat.ai.engines.deepseekharness

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppScope

/**
 * Launch settings backed by the process environment until the settings page owns them.
 *
 * `HEARTBEAT_DSH_EXECUTABLE` overrides the executable. The recommended executable is the
 * `dsh-acp-server` launcher: it starts `dsh --profile acp` and bootstraps the profile on first use.
 * A plain `dsh` needs `--profile acp`; the fully qualified `dsh-acp-server` needs no arguments.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class DefaultDeepSeekLaunchSettings @Inject constructor() : DeepSeekLaunchSettings {

    override fun current(): DeepSeekLaunchConfig {
        val executable = System.getenv(EXECUTABLE_ENV)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_EXECUTABLE
        return DeepSeekLaunchConfig(
            executable = executable,
            arguments = argumentsFor(executable),
        )
    }

    private fun argumentsFor(executable: String): List<String> {
        val name = executable.replace('\\', '/').substringAfterLast('/')
        val isLauncher = name.contains("acp-server")
        return if (isLauncher) listOf() else DEFAULT_ARGUMENTS
    }

    private companion object {
        private const val EXECUTABLE_ENV = "HEARTBEAT_DSH_EXECUTABLE"
        private const val DEFAULT_EXECUTABLE = "dsh-acp-server"
        private val DEFAULT_ARGUMENTS = listOf("--profile", "acp")
    }
}
