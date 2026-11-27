package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.key.Key
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.LocalPromptLink

/** Displays the local Markdown links as compact labels while keeping their destinations in the draft. */
internal class PromptLinkTransformation(
    private val source: String,
    private val style: SpanStyle = SpanStyle(),
) : VisualTransformation {

    private val segments = mutableListOf<Link>()
    val rendered = buildAnnotatedString {
        var start = 0
        for (match in LocalPromptLink.findIn(source)) {
            val partStart = length
            append(source, start, match.range.first)
            addReferenceOffsets(start, match.range.first, partStart, length)
            val first = length
            withStyle(style) { appendPromptLinkLabel(match.label) }
            start = match.range.last + 1
            segments += Link(TextRange(match.range.first, start), TextRange(first, length))
            addReferenceOffsets(match.range.first, start, first, length)
        }
        val last = length
        append(source, start, source.length)
        addReferenceOffsets(start, source.length, last, length)
    }

    val displayText: String get() = rendered.text

    val displayLinks: List<TextRange> get() = segments.map(Link::display)

    private val offsets = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = displayOffset(offset)

        override fun transformedToOriginal(offset: Int): Int {
            var shift = 0
            for (link in segments) {
                if (offset <= link.display.start) break
                if (offset < link.display.end) {
                    val midpoint = (link.display.start + link.display.end) / 2
                    return if (offset <= midpoint) link.original.start else link.original.end
                }
                shift = link.original.end - link.display.end
            }
            return offset + shift
        }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        if (source != text.text) return PromptLinkTransformation(text.text, style).filter(text)
        return if (segments.isEmpty()) TransformedText(text, OffsetMapping.Identity) else TransformedText(rendered, offsets)
    }

    fun displayOffset(offset: Int): Int {
        var shift = 0
        for (link in segments) {
            if (offset <= link.original.start) break
            if (offset < link.original.end) return link.display.start
            shift = link.display.end - link.original.end
        }
        return offset + shift
    }

    fun editAtBoundary(field: TextFieldValue, key: Key): TextFieldValue? {
        if (!field.selection.collapsed || field.composition != null) return null
        val caret = field.selection.end
        val link = when (key) {
            Key.DirectionRight, Key.Delete -> segments.firstOrNull { it.original.start == caret }
            Key.DirectionLeft, Key.Backspace -> segments.firstOrNull { it.original.end == caret }
            else -> null
        } ?: return null
        val range = link.original
        return when (key) {
            Key.DirectionLeft -> field.copy(selection = TextRange(range.start))
            Key.DirectionRight -> field.copy(selection = TextRange(range.end))
            else -> TextFieldValue(field.text.removeRange(range.start, range.end), TextRange(range.start))
        }
    }

    private data class Link(val original: TextRange, val display: TextRange)
}

internal const val PromptLinkAnnotation = "prompt-link"

internal fun AnnotatedString.Builder.appendPromptLinkLabel(label: String) {
    val start = length
    append("\u2003\u2003$label\u00A0")
    addStringAnnotation(PromptLinkAnnotation, "", start, length)
}
