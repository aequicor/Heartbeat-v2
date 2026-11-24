package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface ToolOutput {
    public data class Content(val value: SessionContent) : ToolOutput

    public data class Diff(
        val path: String,
        val oldText: String?,
        val newText: String,
        val scope: DiffScope = DiffScope.File,
    ) : ToolOutput

    public enum class DiffScope { File, Replacement, AllOccurrences }

    /** Captured command output. An absent exit code means the engine did not report one. */
    public data class Command(
        val stdout: String,
        val stderr: String,
        val exitCode: Int?,
    ) : ToolOutput

    public data class Terminal(val id: String) : ToolOutput
}
