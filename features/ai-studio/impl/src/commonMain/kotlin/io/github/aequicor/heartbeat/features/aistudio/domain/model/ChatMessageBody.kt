package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatMessageBody(
    val reply: ChatSourceSnapshot? = null,
    val content: List<Node> = emptyList(),
) {

    val text: String
        get() = content.filterIsInstance<Node.Text>().joinToString(separator = "") { it.value }

    val hasQuotes: Boolean
        get() = content.any { it is Node.Quote }

    fun canSend(hasAttachments: Boolean, hasActiveTurn: Boolean): Boolean =
        !hasActiveTurn && (text.isNotBlank() || hasQuotes || hasAttachments)

    sealed interface Node {

        data class Text(val value: String) : Node

        data class Quote(val snapshot: ChatSourceSnapshot) : Node
    }
}
