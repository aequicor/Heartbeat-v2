package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraMessageReference

internal class AuroraQuoteVisualTransformation(
    val links: PromptLinkTransformation,
    quotes: List<AuroraQuoteEditorBuffer.Quote>,
    marker: String,
    rowHeight: (Int) -> TextUnit,
) : VisualTransformation {

    private val source = links.rendered
    private val sourceToDisplay = IntArray(source.length + 1)
    private val displayToSource = mutableListOf(0)
    val bands: List<Band>
    val rendered: AnnotatedString

    init {
        val positions = mutableListOf<Band>()
        rendered = buildAnnotatedString {
            fun appendSource(start: Int, end: Int) {
                // Each text run already has a paragraph boundary against a quote. Reuse the
                // preceding quote's boundary, or the following one for the leading text run.
                val leadingBreak = if (positions.isEmpty()) 0 else source.text.lineBreakLengthAt(start)
                val trailingBreak = if (positions.isEmpty() && end < source.length) source.text.lineBreakLengthBefore(end) else 0
                if (leadingBreak == 0) sourceToDisplay[start] = length
                repeat(leadingBreak) { append(CaretAnchor) }
                append(source, start + leadingBreak, end - trailingBreak)
                repeat(trailingBreak) { append(CaretAnchor) }
                for (i in start + 1..end) {
                    sourceToDisplay[i] = displayToSource.size
                    displayToSource.add(i)
                }
            }

            var start = 0
            for (quote in quotes) {
                val offset = links.displayOffset(quote.offset)
                if (start < offset) {
                    appendSource(start, offset)
                    // Keep the editable boundary on the preceding paragraph when it has text.
                    append(CaretAnchor)
                    displayToSource.add(offset)
                }
                val lineStart = length
                if (offset == 0) sourceToDisplay[offset] = lineStart
                append(marker)
                marker.indices.forEach { index ->
                    displayToSource.add(if (index + 1 <= marker.length / 2) offset else offset + 1)
                }
                sourceToDisplay[offset + 1] = length
                append(CaretAnchor)
                displayToSource.add(offset + 1)
                positions.add(Band(lineStart, length, quote.reference))
                start = offset + 1
            }
            if (start < source.length) appendSource(start, source.length)
            positions.forEachIndexed { index, band ->
                addStyle(
                    ParagraphStyle(
                        lineHeight = rowHeight(index),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None),
                    ),
                    band.start, band.end,
                )
            }
        }
        bands = positions
    }

    fun displayOffset(offset: Int) = sourceToDisplay[links.displayOffset(offset)]

    fun displayRange(range: TextRange) = TextRange(
        sourceToDisplay[range.start], sourceToDisplay[range.end],
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val linkOffsets = links.filter(text).offsetMapping
        return TransformedText(rendered, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = displayOffset(offset)

            override fun transformedToOriginal(offset: Int): Int =
                linkOffsets.transformedToOriginal(displayToSource[offset])
        })
    }

    data class Band(
        val start: Int,
        val end: Int,
        val reference: AuroraMessageReference,
    )
}

private const val CaretAnchor = '\u200B'
