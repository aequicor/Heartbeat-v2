package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraDraftReferences.Part
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraMessageReference

internal class AuroraQuoteEditorBuffer(val parts: List<Part>) {

    val quotes: List<Quote>
    val text: String

    init {
        val content = StringBuilder()
        quotes = buildList {
            parts.forEach { part ->
                when (part) {
                    is Part.Text -> content.append(part.value)
                    is Part.Quote -> {
                        add(Quote(content.length, part.reference))
                        content.append(QuoteCharacter)
                    }
                }
            }
        }
        text = content.toString()
    }

    fun offsetFor(cursor: AuroraReferenceInteraction.Cursor): Int {
        val preceding = parts.take(cursor.index).sumOf { it.length }
        return (preceding + cursor.offset).coerceIn(0, text.length)
    }

    fun quoteIndexAt(caret: Int, preferred: Int?): Int {
        val offset = when {
            preferred != null -> preferred
            caret == 0 -> 0
            caret == text.length || text.lineBreakLengthAt(caret) > 0 || quotes.any { it.offset == caret } -> caret - 1
            else -> return -1
        }
        return quotes.indexOfFirst { it.offset == offset }
    }

    fun cursorAt(offset: Int): AuroraReferenceInteraction.Cursor {
        var start = 0
        parts.forEachIndexed { index, part ->
            if (part is Part.Text && offset <= start + part.length) {
                return AuroraReferenceInteraction.Cursor(index, (offset - start).coerceAtLeast(0))
            }
            start += part.length
        }
        val last = parts.lastIndex.coerceAtLeast(0)
        return AuroraReferenceInteraction.Cursor(last, (parts.getOrNull(last) as? Part.Text)?.length ?: 0)
    }

    fun insertAtQuoteEdge(previous: TextFieldValue, next: TextFieldValue, quoteOffset: Int): TextFieldValue {
        val offset = previous.selection.end
        val insertedLength = next.text.length - previous.text.length
        if (!previous.selection.collapsed || insertedLength <= 0) return next
        val before = offset == quoteOffset
        val insertedEnd = offset + insertedLength
        val neighbor = if (before) offset - 1 else offset
        val adjacent = previous.text.getOrNull(neighbor) ?: return next
        if (adjacent.isLineBreak() || quotes.any { it.offset == neighbor }) return next
        val separator = if (before) offset else insertedEnd
        val insertedBreakOnly = !before && insertedLength == next.text.lineBreakLengthAt(offset)
        if (!insertedBreakOnly && next.text[if (before) offset else insertedEnd - 1].isLineBreak()) return next
        if (!previous.text.regionMatches(0, next.text, 0, offset) ||
            !previous.text.regionMatches(offset, next.text, insertedEnd, previous.text.length - offset)
        ) return next

        // Keep both the insertion caret and an active IME range on the typed side of the separator.
        val shiftFrom = if (before) separator else separator + 1
        return next.copy(
            annotatedString = buildAnnotatedString {
                append(next.annotatedString, 0, separator)
                append('\n')
                append(next.annotatedString, separator, next.text.length)
            },
            selection = next.selection.shiftAfter(shiftFrom),
            composition = next.composition?.shiftAfter(shiftFrom),
        )
    }

    fun edit(previous: TextFieldValue, next: TextFieldValue): AuroraQuoteEditorBuffer {
        val prefix = previous.text.commonPrefixWith(next.text).length
        val suffix = previous.text.commonSuffixWith(next.text).length
        val unchanged = minOf(prefix + suffix, previous.text.length, next.text.length)
        // The IME may deliver an edit before a pointer selection reaches it. Only use the
        // selection to disambiguate equally small edits (such as deleting one of two quotes).
        val caret = minOf(previous.selection.min, next.selection.min)
        val start = caret.coerceIn(unchanged - suffix, prefix)
        val replacedEnd = previous.text.length - (unchanged - start)
        val shift = next.text.length - previous.text.length
        val remaining = quotes.mapNotNull { quote ->
            when {
                quote.offset < start -> quote
                quote.offset >= replacedEnd -> quote.copy(offset = quote.offset + shift)
                else -> null
            }
        }
        return AuroraQuoteEditorBuffer(buildList {
            var textStart = 0
            remaining.forEach { quote ->
                add(Part.Text(next.text.substring(textStart, quote.offset)))
                add(Part.Quote(quote.reference))
                textStart = quote.offset + 1
            }
            add(Part.Text(next.text.substring(textStart)))
        })
    }

    fun removeEmptyQuoteRow(next: TextFieldValue): TextFieldValue {
        val offset = next.selection.end
        if (!next.selection.collapsed || text.length != next.text.length + 1 || quotes.none { it.offset == offset }) {
            return next
        }
        if (!text.regionMatches(0, next.text, 0, offset) ||
            !text.regionMatches(offset + 1, next.text, offset, next.text.length - offset)
        ) return next

        val after = next.text.lineBreakLengthAt(offset)
        val before = when (next.text.getOrNull(offset - 1)) {
            '\n' -> if (next.text.getOrNull(offset - 2) == '\r') 2 else 1
            '\r' -> 1
            else -> 0
        }
        val range = when {
            after > 0 && (before > 0 || offset == 0) -> TextRange(offset, offset + after)
            before > 0 && offset == next.text.length -> TextRange(offset - before, offset)
            else -> return next
        }
        return next.copy(
            annotatedString = next.annotatedString.subSequence(0, range.start) +
                next.annotatedString.subSequence(range.end, next.text.length),
            selection = TextRange(range.start),
            composition = null,
        )
    }

    private val Part.length: Int
        get() = when (this) {
            is Part.Text -> value.length
            is Part.Quote -> 1
        }

    data class Quote(val offset: Int, val reference: AuroraMessageReference)
}

private fun Char.isLineBreak() = this == '\n' || this == '\r'

internal fun String.lineBreakLengthAt(offset: Int): Int = when (getOrNull(offset)) {
    '\r' -> if (getOrNull(offset + 1) == '\n') 2 else 1
    '\n' -> 1
    else -> 0
}

private fun TextRange.shiftAfter(offset: Int) = TextRange(
    if (start >= offset) start + 1 else start,
    if (end >= offset) end + 1 else end,
)

internal const val QuoteCharacter = '\uFFFC'
