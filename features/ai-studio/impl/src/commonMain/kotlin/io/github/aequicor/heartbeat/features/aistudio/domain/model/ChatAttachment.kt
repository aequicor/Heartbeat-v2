package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatAttachment(
    val id: String, val name: String, val kind: String, val content: String,
    val date: Date?, val storageId: String?, val size: Long?,
) {

    data class Date(val year: Int, val month: Int, val day: Int, val time: String)
}
