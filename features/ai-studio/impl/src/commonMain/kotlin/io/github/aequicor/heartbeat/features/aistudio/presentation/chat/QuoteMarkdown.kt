package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceReference.Range
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyAlignment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyInline

internal fun String.formatQuoteMarkdown(range: Range): String? {
    val document = try {
        ReplyMarkdownParser().parse(this, final = true)
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (document.plainText != null) return null
    val display = document.blocks.clipMarkdown(this, range)
    return display?.takeUnless { it == substring(range.start, range.endExclusive) }
}

private fun List<ReplyBlock>.clipMarkdown(source: String, range: Range): String? = mapNotNull { block ->
    when (block) {
        is ReplyBlock.Paragraph -> block.lines.map { it.clipInlines(source, range) }.trimEmptyLines()
        is ReplyBlock.Heading -> block.text.clipInlines(source, range).takeIf(String::isNotBlank)?.let {
            "${"#".repeat(block.level)} $it"
        }
        is ReplyBlock.Quote -> block.blocks.clipMarkdown(source, range)?.prependIndent("> ")
        is ReplyBlock.ListBlock -> block.items.mapIndexedNotNull { index, item ->
            val text = item.clipMarkdown(source, range) ?: return@mapIndexedNotNull null
            val marker = if (block.ordered) "${block.start + index}. " else "- "
            val check = when (block.checked.getOrNull(index)) {
                true -> "[x] "
                false -> "[ ] "
                null -> ""
            }
            marker + check + text.lineSequence().joinToString("\n" + " ".repeat(marker.length))
        }.joinToString("\n").takeIf(String::isNotBlank)
        is ReplyBlock.Table -> block.clipTable(source, range)
        is ReplyBlock.Code -> source.clipCode(block.id, block.source, block.language.orEmpty(), range)
        is ReplyBlock.Diagram -> source.clipCode(block.id, block.source, block.kind.name.lowercase(), range)
        is ReplyBlock.Rule -> {
            val end = source.indexOf('\n', block.id).takeIf { it >= 0 } ?: source.length
            source.substring(block.id, end).takeIf { block.id >= range.start && end <= range.endExclusive }
        }
    }
}.joinToString("\n\n").takeIf(String::isNotBlank)

private fun ReplyBlock.Table.clipTable(source: String, range: Range): String? {
    val headers = header.map { it.clipInlines(source, range) }
    val body = rows.map { row -> row.map { it.clipInlines(source, range) } }.filter { row -> row.any(String::isNotBlank) }
    if (headers.none(String::isNotBlank) && body.isEmpty()) return null
    val markers = alignments.map {
        when (it) {
            ReplyAlignment.Left -> "---"
            ReplyAlignment.Center -> ":---:"
            ReplyAlignment.Right -> "---:"
        }
    }
    return (listOf(headers, markers) + body).joinToString("\n") { cells ->
        cells.joinToString(" | ", prefix = "| ", postfix = " |")
    }
}

private fun List<ReplyInline>.clipInlines(source: String, range: Range): String = buildString {
    var style: ReplyInline? = null
    val text = StringBuilder()

    fun flush() {
        style?.let { append(it.wrapInline(text.toString())) }
        text.clear()
    }

    for (span in this@clipInlines) {
        val original = span.originalRange ?: continue
        val first = maxOf(original.first, range.start)
        val last = minOf(original.last + 1, range.endExclusive)
        if (first >= last) continue
        if (span.attachmentReference && first == original.first && last == original.last + 1) {
            flush()
            append(source.substring(first, last))
            continue
        }
        val rawLength = original.last + 1 - original.first
        val from = ((first - original.first).toLong() * span.text.length / rawLength).toInt()
        val to = ((last - original.first).toLong() * span.text.length / rawLength).toInt()
        val next = span.copy(text = "", originalRange = null)
        if (next != style) {
            flush()
            style = next
        }
        text.append(span.text, from, to)
    }
    flush()
}

private fun ReplyInline.wrapInline(text: String): String {
    if (text.isBlank()) return text
    val value = text.trim()
    var formatted = if (code) {
        val fence = "`".repeat(value.longestBacktickRun() + 1)
        val delimiter = if (value.startsWith('`') || value.endsWith('`')) "$fence " else fence
        val end = if (delimiter == fence) fence else " $fence"
        delimiter + value + end
    } else value.escapeMarkdown()
    if (bold) formatted = "**$formatted**"
    if (italic) formatted = "*$formatted*"
    if (strike) formatted = "~~$formatted~~"
    url?.let { formatted = "[$formatted](<$it>)" }
    return text.takeWhile(Char::isWhitespace) + formatted + text.takeLastWhile(Char::isWhitespace)
}

private fun String.clipCode(id: Int, code: String, language: String, range: Range): String? {
    if (code.isEmpty()) return null
    val start = indexOf(code, id).takeIf { it >= 0 } ?: return null
    val from = maxOf(start, range.start)
    val to = minOf(start + code.length, range.endExclusive)
    if (from >= to) return null
    val text = substring(from, to)
    val fence = "`".repeat(maxOf(3, text.longestBacktickRun() + 1))
    return "$fence$language\n$text\n$fence"
}

private fun List<String>.trimEmptyLines(): String? {
    val first = indexOfFirst(String::isNotBlank)
    val end = indexOfLast(String::isNotBlank)
    return if (first < 0) null else subList(first, end + 1).joinToString("\n")
}

private fun String.longestBacktickRun() = backticks.findAll(this).maxOfOrNull { it.value.length } ?: 0

private fun String.escapeMarkdown() = buildString {
    for (character in this@escapeMarkdown) {
        if (character in "\\*`_~[]#|<>") append('\\')
        append(character)
    }
}

private val backticks = Regex("`+")
