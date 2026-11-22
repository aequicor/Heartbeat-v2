package io.github.aequicor.heartbeat.features.aistudio.data.model

internal data class ChatTurnMessageRecord(
    val id: String,
    val role: String,
    val status: String,
    val header: String,
    val parts: List<Part>,
) {

    data class Part(
        val kind: String,
        val body: String? = null,
        val parent: Int? = null,
        val title: String? = null,
        val status: String? = null,
        val toolId: String? = null,
        val header: String? = null,
        val attachment: Attachment? = null,
    )

    data class Attachment(val existingId: String?, val name: String, val content: String, val header: String)
}
