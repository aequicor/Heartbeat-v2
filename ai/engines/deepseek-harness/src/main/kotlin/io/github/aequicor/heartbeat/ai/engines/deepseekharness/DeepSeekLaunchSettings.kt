package io.github.aequicor.heartbeat.ai.engines.deepseekharness

import io.github.aequicor.heartbeat.ai.engine.acp.AcpLaunchSpec

/**
 * Launch configuration of the agent, read on every connect.
 *
 * The settings page replaces [DefaultDeepSeekLaunchSettings] when it exists; the provider depends
 * only on this contract, so a changed executable applies to the next connection without recreating
 * the engine.
 */
public interface DeepSeekLaunchSettings {
    public fun current(): DeepSeekLaunchConfig
}

/** Values the provider needs to start the agent; secrets stay in the process environment. */
public data class DeepSeekLaunchConfig(
    val executable: String,
    val arguments: List<String> = listOf(),
    val workingDirectory: String? = null,
)

internal fun DeepSeekLaunchConfig.toAcpLaunchSpec(): AcpLaunchSpec = AcpLaunchSpec(
    executable = executable,
    arguments = arguments,
    workingDirectory = workingDirectory,
)
