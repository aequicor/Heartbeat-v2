package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatAttachment(
    val id: String, val name: String, val kind: String, val content: String,
    val date: Date?, val storageId: String?, val size: Long?,
    val image: Image? = null,
    val mimeType: String? = null,
    val base64Encoded: Boolean = false,
    val source: AttachmentFile? = null,
    val transferMode: AttachmentTransferMode = AttachmentTransferMode.Resource,
) {

    data class Date(val year: Int, val month: Int, val day: Int, val time: String)
    data class Image(val mimeType: String, val thumbnail: String?)
}

internal fun isMarkdownAttachment(name: String, kind: String? = null): Boolean =
    kind.equals("Markdown", ignoreCase = true) ||
        name.substringAfterLast('.', "").lowercase() in MarkdownAttachmentExtensions

private val MarkdownAttachmentExtensions = setOf("md", "markdown")
