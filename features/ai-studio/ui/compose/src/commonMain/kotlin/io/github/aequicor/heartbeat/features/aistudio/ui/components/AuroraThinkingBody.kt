package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors

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
    val shown = if (selecting) viewport.displayed else viewport.displayed.update(text)
    SideEffect { viewport.displayed = shown }

    shown.blocks.forEachIndexed { index, block ->
        key(index) {
            AuroraWorkText(block, AuroraWorkRole.Detail, WorkDetailWidth, color = AuroraColors.DesktopInk)
        }
    }
}

private class ThinkingViewport(var displayed: ThinkingText)

private class ThinkingText(
    private val source: String,
    val blocks: List<String>,
    private val tailStart: Int,
) {
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
