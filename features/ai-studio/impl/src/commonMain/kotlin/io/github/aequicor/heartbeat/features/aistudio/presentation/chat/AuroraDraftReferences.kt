package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody.Node
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyHistoryContent

@ConsistentCopyVisibility
public data class AuroraDraftReferences internal constructor(internal val body: ChatMessageBody) {

    public val reply: AuroraMessageReference? = body.reply?.let(::AuroraMessageReference)

    public val nodes: List<Part> = buildList {
        var offset = 0
        for (node in body.content) {
            when (node) {
                is Node.Text -> {
                    add(Part.Text(node.value, offset))
                    offset += node.value.length
                }
                is Node.Quote -> {
                    add(Part.Quote(AuroraMessageReference(node.snapshot)))
                    offset += node.snapshot.fragments.sumOf { it.text.length }
                }
            }
        }
    }

    public val originalText: String get() = body.originalReferenceText()

    public val hasQuotes: Boolean get() = body.hasQuotes

    public sealed interface Part {

        public data class Text(val value: String, val sourceOffset: Int = 0) : Part {

            public val markdownContent: ReplyHistoryContent = ReplyHistoryContent(value)
        }

        public data class Quote(val reference: AuroraMessageReference) : Part
    }
}

internal fun ChatMessageBody.originalReferenceText(): String = content.joinToString("") {
    when (it) {
        is Node.Text -> it.value
        is Node.Quote -> it.snapshot.originalText
    }
}

internal fun ChatMessageBody.displayReferences(): AuroraDraftReferences? =
    takeIf { reply != null || hasQuotes }?.let(::AuroraDraftReferences)

internal fun Draft.body() = references?.body ?: ChatMessageBody(content = listOf(Node.Text(text)))

internal fun Draft.withBody(body: ChatMessageBody) = copy(text = body.text, references = body.displayReferences())

internal fun Draft.withEditedContent(parts: List<AuroraDraftReferences.Part>): Draft =
    withBody(body().copy(content = parts.map { part ->
        when (part) {
            is AuroraDraftReferences.Part.Text -> Node.Text(part.value)
            is AuroraDraftReferences.Part.Quote -> Node.Quote(part.reference.snapshot)
        }
    }))

internal fun ChatMessageBody.editText(index: Int, value: String): ChatMessageBody =
    copy(content = content.mapIndexed { part, node -> if (part == index && node is Node.Text) Node.Text(value) else node })

internal fun ChatMessageBody.insertQuotes(index: Int, offset: Int, quotes: List<Node.Quote>): ChatMessageBody {
    if (quotes.isEmpty()) return this
    val position = index.takeIf { content.getOrNull(it) is Node.Text }
        ?: content.indexOfLast { it is Node.Text }.coerceAtLeast(0)
    val text = (content.getOrNull(position) as? Node.Text)?.value.orEmpty()
    val splitAt = offset.coerceIn(0, text.length)
    val inserted = buildList {
        add(Node.Text(text.substring(0, splitAt)))
        quotes.forEachIndexed { quoteIndex, quote ->
            add(quote)
            if (quoteIndex != quotes.lastIndex) add(Node.Text(""))
        }
        add(Node.Text(text.substring(splitAt)))
    }
    return copy(content = content.take(position) + inserted + content.drop(position + 1))
}

internal fun ChatMessageBody.removeQuote(index: Int): ChatMessageBody {
    if (content.getOrNull(index) !is Node.Quote) return this
    val before = (content.getOrNull(index - 1) as? Node.Text)?.value.orEmpty()
    val after = (content.getOrNull(index + 1) as? Node.Text)?.value.orEmpty()
    return copy(content = content.take((index - 1).coerceAtLeast(0)) + Node.Text(before + after) + content.drop(index + 2))
}
