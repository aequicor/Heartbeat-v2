package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyCodeLine

/** A confirmed, unindented top-level fence consumes raw source until a possible closing line. */
internal class OpenReplyFence(
    private val opening: String,
    private val bodyStart: Int,
    private val prefix: List<ReplyBlock>,
    private val block: ReplyBlock,
) {
    private val highlighter = (block as? ReplyBlock.Code)?.let { ReplyCodeHighlighter(it.language) }
    private val lines = mutableListOf<ReplyCodeLine>()
    private var lineStart = bodyStart
    private var cursor = bodyStart

    fun advance(source: String): ReplyDocument? {
        // A carriage return at the chunk boundary can still become part of CRLF.
        val end = if (source.endsWith('\r')) source.lastIndex else source.length
        if (!consumeLines(source, end)) return null
        val body = source.substring(bodyStart, end)
        val preview = when (block) {
            is ReplyBlock.Code -> {
                val snapshot = if (lineStart < end) {
                    lines + checkNotNull(highlighter).line(source.substring(lineStart, end), commit = false)
                } else lines.toList()
                block.copy(source = body, lines = snapshot)
            }
            is ReplyBlock.Diagram -> block.copy(source = body)
            else -> error("A fence contains code or diagram source")
        }
        return ReplyDocument(prefix + preview)
    }

    private fun consumeLines(source: String, end: Int): Boolean {
        while (cursor < end) {
            val position = cursor++
            val char = source[position]
            if (char != '\r' && char != '\n') continue
            if (isClosingLine(source, position)) return false
            if (highlighter != null) lines += highlighter.line(source.substring(lineStart, position))
            if (char == '\r' && source.getOrNull(cursor) == '\n') cursor++
            lineStart = cursor
        }
        // JetBrains Markdown resolves a closing fence at end of input without a newline.
        return !isClosingLine(source, end)
    }

    private fun isClosingLine(source: String, end: Int): Boolean {
        var index = lineStart
        while (index < end && source[index] == ' ' && index - lineStart <= MaxClosingIndent) index++
        val markerStart = index
        while (index < end && source[index] == opening.first()) index++
        return markerStart - lineStart <= MaxClosingIndent &&
            index - markerStart >= opening.length &&
            (index until end).all { source[it] == ' ' || source[it] == '\t' }
    }
}

private const val MaxClosingIndent = 3
