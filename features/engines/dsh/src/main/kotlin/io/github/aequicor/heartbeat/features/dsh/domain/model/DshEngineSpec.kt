package io.github.aequicor.heartbeat.features.dsh.domain.model

public data class DshEngineSpec(val command: List<String>) {
    public val id: String = "dsh"
    public val name: String = "DeepSeek Harness"
    public val description: String = "DeepSeek Harness ACP agent"
}
