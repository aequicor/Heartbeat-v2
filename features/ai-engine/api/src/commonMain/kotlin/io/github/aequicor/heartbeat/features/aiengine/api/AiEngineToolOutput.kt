package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineToolOutput {
    public data class Content(val value: AiEngineSessionContent) : AiEngineToolOutput

    public data class Diff(
        val path: String,
        val oldText: String?,
        val newText: String,
    ) : AiEngineToolOutput

    public data class Terminal(val id: String) : AiEngineToolOutput
}
