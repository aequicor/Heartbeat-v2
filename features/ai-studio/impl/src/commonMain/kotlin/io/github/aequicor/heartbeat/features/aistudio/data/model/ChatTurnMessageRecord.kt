package io.github.aequicor.heartbeat.features.aistudio.data.model

internal data class ChatTurnMessageRecord(
    val id: String,
    val role: String,
    val status: String,
    val header: String,
    val parts: List<Part>,
    val expectedRevisionId: Long? = null,
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
        val snapshot: SentAttachmentRecord? = null,
    )

    data class Attachment(
        val existingId: String?,
        val name: String,
        val mimeType: String,
        val content: String,
        val header: String,
        val size: Long,
        val originalUri: String? = null,
        val managedPath: String? = null,
        val base64Encoded: Boolean = false,
        val description: String? = null,
        val linkUri: String? = null,
    )
}
