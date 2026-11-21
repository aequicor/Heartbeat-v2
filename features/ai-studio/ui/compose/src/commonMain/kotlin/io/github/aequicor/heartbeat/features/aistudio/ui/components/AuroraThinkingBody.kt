package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.ceil
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus

private const val ThinkingLinesPerBlock = 32

@Composable
internal fun AuroraThinkingBody(
    eventId: String,
    text: String,
    selection: SelectionState,
) {
    val selecting by remember(selection) { derivedStateOf { selection.selectedTexts.isNotEmpty() } }
    val viewport = remember(eventId) { ThinkingViewport(splitThinkingText(text, listOf(), 0)) }
    // Foundation releases the whole selection when one of its selected text nodes changes.
    val shown = if (selecting && !replyRevealIsFinal()) viewport.displayed else viewport.displayed.update(text)
    SideEffect { viewport.displayed = shown }

    shown.blocks.forEachIndexed { index, block ->
        key(index) {
            val tail = remember { ThinkingTextTail() }
            val modifier = if (index == shown.blocks.lastIndex) WorkDetailWidth.then(tail.modifier) else WorkDetailWidth
            AuroraReplyText(
                block, modifier, HbTypography.Detail.copy(color = AuroraColors.DesktopInk),
                onTextLayout = tail::update, revealSource = shown.source, revealOffset = shown.offsets[index],
            )
        }
    }
}

@Composable
internal fun AuroraThinkingPreview(event: WorkEvent, color: Color, modifier: Modifier = Modifier) {
    if (event.summary.isBlank()) {
        AuroraWorkText(event.preview, AuroraWorkRole.Summary, modifier, color, maxLines = 2)
        return
    }
    key(event.id) {
        AuroraReplyReveal(streaming = event.status == StepStatus.Current) {
            AuroraReplyText(event.preview, modifier, HbTypography.Caption.copy(color = color),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
private class ThinkingViewport(var displayed: ThinkingText)

private class ThinkingText(
    val source: String,
    val blocks: List<String>,
    private val tailStart: Int,
) {
    val offsets = blocks.runningFold(0) { offset, block -> offset + block.length + 1 }

    fun update(text: String): ThinkingText = when {
        text == source -> this
        text.startsWith(source) -> splitThinkingText(text, blocks.subList(0, blocks.lastIndex), tailStart)
        else -> splitThinkingText(text, listOf(), 0)
    }
}

private fun splitThinkingText(source: String, prefix: List<String>, tailStart: Int): ThinkingText {
    val blocks = prefix.toMutableList()
    var start = tailStart
    var cursor = tailStart
    var lines = 0
    while (cursor < source.length) {
        val newline = source.indexOf('\n', cursor)
        // Keep a trailing blank line in the existing text node until another line is appended.
        if (newline < 0 || newline == source.lastIndex) break
        lines++
        if (lines >= ThinkingLinesPerBlock && source.getOrNull(newline - 1) != '\r') {
            // SelectionContainer inserts this same newline when copying across the two nodes.
            blocks.add(source.substring(start, newline))
            start = newline + 1
            lines = 0
        }
        cursor = newline + 1
    }
    blocks.add(if (start == 0) source else source.substring(start))
    return ThinkingText(source, blocks, start)
}

/** Keeps trailing paragraph separators copyable without reserving blank rows below thinking. */
internal class ThinkingTextTail : LayoutModifier {
    private var height: Int? = null
    val modifier: Modifier = Modifier.clipToBounds().then(this)

    fun update(result: TextLayoutResult) {
        val last = result.layoutInput.text.indexOfLast { !it.isWhitespace() }
        height = if (last < 0) 0 else ceil(result.getLineBottom(result.getLineForOffset(last))).toInt()
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height)

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints) =
        measurable.measure(constraints).let { text ->
            val shown = constraints.constrainHeight(minOf(text.height, height ?: text.height))
            layout(text.width, shown) { text.placeRelative(0, 0) }
        }
}
