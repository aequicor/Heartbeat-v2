package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface SessionContent {
    public data class Text(val text: String) : SessionContent

    public data class Resource(
        val uri: String,
        val mimeType: String?,
        val title: String?,
    ) : SessionContent
}
