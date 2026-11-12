package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface ToolOutput {
    public data class Content(val value: SessionContent) : ToolOutput

    public data class Diff(
        val path: String,
        val oldText: String?,
        val newText: String,
    ) : ToolOutput

    public data class Terminal(val id: String) : ToolOutput
}
