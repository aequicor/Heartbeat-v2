package io.github.aequicor.heartbeat.ai.engine.acp

/**
 * Command that starts an ACP agent.
 *
 * The provider computes the spec on every connect, so settings and credentials changed after the
 * engine was created apply to the next connection. Secrets travel only in [environment] and never
 * appear in the engine key, states or logs.
 */
public data class AcpLaunchSpec(
    val executable: String,
    val arguments: List<String> = listOf(),
    val environment: Map<String, String> = mapOf(),
    val workingDirectory: String? = null,
) {
    override fun toString(): String = "AcpLaunchSpec"
}
