package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraReferenceSelection
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraReferenceSelection.Kind
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

internal val LocalAuroraReferenceRegion = compositionLocalOf<AuroraReferenceRow.Region?> { null }
internal val LocalAuroraReferenceHighlight = compositionLocalOf<AuroraReferenceHighlight?> { null }
internal val LocalAuroraReferenceSourceOffset = compositionLocalOf { 0 }

@Composable
internal fun AuroraReferenceRegion(
    source: AuroraReferenceSelection?,
    rawText: String,
    selection: SelectionState? = null,
    modifier: Modifier = Modifier,
    objectSelectable: Boolean = false,
    content: @Composable () -> Unit,
) {
    val row = LocalAuroraReferenceRow.current
    if (row == null || source == null) {
        if (modifier == Modifier) content() else Box(modifier) { content() }
        return
    }
    val exactSource = source.copy(revisionId = source.revisionId ?: row.source.revisionId)
    val region = remember(row, exactSource, selection, rawText, objectSelectable) {
        AuroraReferenceRow.Region(exactSource, selection, rawText, objectSelectable)
    }
    DisposableEffect(row, region) {
        row.regions.add(region)
        onDispose { row.regions.remove(region) }
    }
    val interaction = LocalAuroraReferenceInteraction.current
    val selected = interaction?.selections?.any { it.range == null && it.selectedText == null && it.matches(source) } == true
    val highlight = LocalAuroraReferenceHighlight.current?.takeIf { it.jump.target.matches(exactSource) }
    val whole = highlight?.jump?.target?.range == null
    val requester = remember { BringIntoViewRequester() }
    val positioned = remember { mutableStateOf(false) }
    LaunchedEffect(highlight, whole) {
        if (highlight == null || !whole) return@LaunchedEffect
        highlight.revealTarget {
            snapshotFlow { positioned.value }.first { it }
            requester.bringIntoView()
        }
    }
    Box(
        modifier.onGloballyPositioned {
            region.bounds = it.boundsInRoot()
            positioned.value = it.size.width > 0 && it.size.height > 0
        }
            .bringIntoViewRequester(requester)
            .drawBehind {
                val opacity = if (selected) 1f else if (whole) highlight?.alpha ?: 0f else 0f
                if (opacity > 0f) drawRect(AuroraSourceHighlight, alpha = opacity * 0.62f)
            },
    ) {
        CompositionLocalProvider(LocalAuroraReferenceRegion provides region, content = content)
    }
}

@Composable
internal fun Modifier.auroraReferenceText(
    text: AnnotatedString,
    sourceOffset: Int,
    layout: () -> TextLayoutResult?,
): Modifier {
    val region = LocalAuroraReferenceRegion.current ?: return this
    val highlight = LocalAuroraReferenceHighlight.current?.takeIf { it.jump.target.matches(region.source) }
    val segmentOffset = LocalAuroraReferenceSourceOffset.current
    val fragment = remember(region, text, sourceOffset, segmentOffset) {
        AuroraReferenceTextFragment(text, region.rawText, sourceOffset, layout, segmentOffset)
    }
    DisposableEffect(region, fragment) {
        region.fragments.add(fragment)
        onDispose { region.fragments.remove(fragment) }
    }
    val range = highlight?.jump?.target?.range
    val visible = fragment.visibleRanges(range)
    val selected = LocalAuroraReferenceInteraction.current?.selections.orEmpty()
        .filter { it.matches(region.source) }.flatMap { fragment.visibleRanges(it.range) }
    val requester = remember { BringIntoViewRequester() }
    val positioned = remember { mutableStateOf(false) }
    fun currentLayout() = layout()?.takeIf { it.layoutInput.text.text == text.text }
    LaunchedEffect(highlight, visible, text) {
        val first = visible.firstOrNull() ?: return@LaunchedEffect
        highlight?.revealTarget {
            val measured = snapshotFlow { currentLayout().takeIf { positioned.value } }.filterNotNull().first()
            val bounds = measured.getPathForRange(first.first, first.last + 1).getBounds()
            requester.bringIntoView(bounds)
        }
    }
    return onGloballyPositioned {
        fragment.bounds = it.boundsInRoot()
        fragment.origin = it.positionInRoot()
        positioned.value = it.size.width > 0 && it.size.height > 0
    }
        .bringIntoViewRequester(requester)
        .drawBehind {
            val alpha = highlight?.alpha ?: 0f
            val current = currentLayout()
            if (current != null) {
                for (part in selected) {
                    drawPath(current.getPathForRange(part.first, part.last + 1), AuroraSourceHighlight, alpha = 0.72f)
                }
            }
            if (current != null && alpha > 0f) {
                for (part in visible) {
                    drawPath(current.getPathForRange(part.first, part.last + 1), AuroraSourceHighlight, alpha = alpha * 0.8f)
                }
            }
        }
}

internal class AuroraReferenceTextFragment(
    private val text: AnnotatedString,
    source: String,
    hint: Int,
    private val layout: () -> TextLayoutResult?,
    segmentOffset: Int = 0,
) {

    private val mappings = text.getStringAnnotations(ReferenceOffsets, 0, text.length).mapNotNull { span ->
        val start = span.item.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
        val end = span.item.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
        Mapping(span.start, span.end, segmentOffset + start, segmentOffset + end)
    }.ifEmpty {
        val start = source.indexOf(text.text, (segmentOffset + hint).coerceIn(0, source.length))
            .takeIf { it >= 0 } ?: source.indexOf(text.text)
        if (start < 0 || text.isEmpty()) emptyList()
        else listOf(Mapping(0, text.length, start, start + text.length))
    }
    var bounds: Rect? = null
    var origin: Offset = Offset.Zero

    fun offsetAt(point: Offset, end: Boolean): Int? {
        if (bounds?.contains(point) != true) return null
        val measured = layout() ?: return null
        return sourceOffsetAt(measured.getOffsetForPosition(point - origin), end)
    }

    fun selectionRange(selected: AnnotatedString): AuroraReferenceSelection.Range? {
        if (selected.isEmpty()) return null
        val annotations = selected.getStringAnnotations(ReferenceOffsets, 0, selected.length)
        var start = text.text.indexOf(selected.text)
        var match: AuroraReferenceSelection.Range? = null
        while (start >= 0) {
            val end = start + selected.length
            val sameSpans = annotations.isEmpty() ||
                text.subSequence(start, end).getStringAnnotations(ReferenceOffsets, 0, selected.length) == annotations
            if (sameSpans) {
                val from = sourceOffsetAt(start, end = false)
                val to = sourceOffsetAt(end, end = true)
                if (from != null && to != null && from < to) {
                    if (match != null) return null
                    match = AuroraReferenceSelection.Range(from, to)
                }
            }
            start = text.text.indexOf(selected.text, start + 1)
        }
        return match
    }

    private fun sourceOffsetAt(offset: Int, end: Boolean): Int? {
        val candidates = mappings.filter { offset in it.displayStart..it.displayEnd }
        val mapping = if (end) candidates.firstOrNull() else candidates.lastOrNull()
        return mapping?.sourceOffset(offset)
    }

    fun visibleRanges(range: AuroraReferenceSelection.Range?): List<IntRange> {
        if (range == null) return emptyList()
        return mappings.mapNotNull { mapping ->
            val from = maxOf(range.start, mapping.sourceStart)
            val until = minOf(range.endExclusive, mapping.sourceEnd)
            if (from >= until) null else mapping.visibleOffset(from) until mapping.visibleOffset(until)
        }.filterNot(IntRange::isEmpty)
    }

    private data class Mapping(val displayStart: Int, val displayEnd: Int, val sourceStart: Int, val sourceEnd: Int) {

        fun sourceOffset(offset: Int): Int = sourceStart +
            ((offset - displayStart).toLong() * (sourceEnd - sourceStart) / (displayEnd - displayStart)).toInt()

        fun visibleOffset(offset: Int): Int = displayStart +
            ((offset - sourceStart).toLong() * (displayEnd - displayStart) / (sourceEnd - sourceStart)).toInt()
    }
}

internal fun AnnotatedString.Builder.addReferenceOffsets(sourceStart: Int, sourceEnd: Int, start: Int, end: Int) {
    if (sourceStart < sourceEnd && start < end) {
        addStringAnnotation(ReferenceOffsets, "$sourceStart:$sourceEnd", start, end)
    }
}

private const val ReferenceOffsets = "aurora-reference-offsets"

internal fun AuroraReferenceSelection.matches(other: AuroraReferenceSelection): Boolean =
    messageId == other.messageId && kind == other.kind && stepId == other.stepId && eventId == other.eventId &&
        attachmentId == other.attachmentId &&
        (revisionId == null || other.revisionId == null || revisionId == other.revisionId)

internal fun AuroraReferenceSelection.replySource(): AuroraReferenceSelection = when (kind) {
    Kind.ToolCall, Kind.ToolResult, Kind.Attachment -> if (eventId == null) {
        AuroraReferenceSelection(messageId, Kind.Message, revisionId = revisionId)
    } else copy(kind = Kind.ToolCall, attachmentId = null, range = null, selectedText = null)
    else -> AuroraReferenceSelection(messageId, Kind.Message, revisionId = revisionId)
}

private val AuroraSourceHighlight = Color(0xFFDCCCF1)
