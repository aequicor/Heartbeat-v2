package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface SessionContent {
    public data class Text(val text: String) : SessionContent

    public data class Artifact(
        val uri: String?,
        val mimeType: String?,
        val payload: Payload,
    ) : SessionContent {
        public sealed interface Payload {
            public data class Text(val text: String) : Payload
            public data class Base64(val data: String) : Payload
        }
    }

    public data class Resource(
        val uri: String,
        val mimeType: String?,
        val title: String?,
    ) : SessionContent
}
