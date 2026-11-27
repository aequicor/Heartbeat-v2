package io.github.aequicor.heartbeat.features.aistudio.data.model

internal data class ChatEntryRecord(
    val position: Long, val revision: Long, val message: Long, val predatesEdit: Boolean,
    val role: String, val header: String, val parts: List<Part>,
) {

    data class Part(
        val id: Long, val parent: Long?, val kind: String, val title: String?, val body: String?,
        val header: String?, val attachment: Attachment?, val deferred: Boolean,
        val snapshot: SentAttachmentRecord? = null,
    )

    data class Attachment(
        val id: String,
        val size: Long,
        val header: String?,
        val content: String?,
        val mimeType: String? = null,
        val transferMode: String? = null,
        val uri: String? = null,
        val binarySnapshot: Boolean = false,
        val originalUri: String? = null,
        val managedPath: String? = null,
        val addedAt: String? = null,
    )

    data class Revision(val role: String, val status: String, val hasAttachments: Boolean)
}
