package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class PromptReference(
    val category: Category,
    val name: String,
    val uri: String,
    val sizeBytes: Long? = null,
    val description: String? = null,
    val image: ChatAttachment.Image? = null,
) {

    enum class Category { SessionFile, ProjectFile, Skill }
}
