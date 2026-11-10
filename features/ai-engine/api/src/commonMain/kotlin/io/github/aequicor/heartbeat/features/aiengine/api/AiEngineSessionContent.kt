package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineSessionContent {
    public data class Text(val text: String) : AiEngineSessionContent

    public data class Resource(
        val uri: String,
        val mimeType: String?,
        val title: String?,
    ) : AiEngineSessionContent
}
