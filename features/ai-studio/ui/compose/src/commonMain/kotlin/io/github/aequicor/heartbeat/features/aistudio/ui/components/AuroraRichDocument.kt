@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument

@Composable
internal fun AuroraRichDocument(
    document: ReplyDocument,
    touch: Boolean,
    selection: SelectionState,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    fallbackText: String = "",
    streaming: Boolean = false,
    preparingHeight: Dp = Dp.Hairline,
    prepared: PreparedDocument? = null,
    archiveReveal: ArchiveReveal? = null,
) {
    val plain = document.plainText
    if (plain != null) {
        val body = archiveReveal?.let { reveal -> Modifier.onPlaced { reveal.markPlaced() } } ?: Modifier
        SelectionContainer(state = selection, modifier = modifier) {
            AuroraReplyText(plain, body, style = auroraMessageStyle(), onTextLayout = onTextLayout)
        }
        return
    }

    val monospace = auroraCodeFontFamily()
    val ready = prepared?.takeIf { it.monospace == monospace } ?: rememberPreparedDocument(document, monospace)
    val visible = selectedPreparedDocument(ready, selection)
    SelectionContainer(state = selection, modifier = modifier) {
        val blocks = visible?.blocks
        when {
            blocks != null -> AuroraDocumentBlocks(blocks, touch, streaming, preparingHeight, archiveReveal)
            fallbackText.isNotEmpty() -> AuroraReplyText(fallbackText, style = auroraMessageStyle())
            preparingHeight > Dp.Hairline -> AuroraPreparingReply(minHeight = preparingHeight)
        }
    }
}

@Composable
internal fun selectedPreparedDocument(latest: PreparedDocument?, selection: SelectionState): PreparedDocument? {
    if (replyRevealIsFinal()) return latest
    val display = remember(selection) { DocumentSelectionRevision() }
    val selected = selection.selectedTexts
    val pinned = if (selected === display.selected) display.pinned else display.visible?.throughSelection(selected)
    val visible = if (selected.isEmpty()) latest else pinned?.appendNewBlocks(latest) ?: latest
    SideEffect {
        display.selected = selected
        display.pinned = if (selected.isEmpty()) null else pinned
        display.visible = visible
    }
    return visible
}

private class DocumentSelectionRevision {
    var selected: List<AnnotatedString> = listOf()
    var pinned: PreparedDocument? = null
    var visible: PreparedDocument? = null
}

@Composable
internal fun AuroraRichBlocks(
    blocks: List<PreparedBlock>,
    touch: Boolean,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    firstGroup: Boolean = true,
    followsHeading: Boolean = false,
    bodyStyle: TextStyle = auroraMessageStyle(),
) {
    Column(modifier) {
        blocks.forEachIndexed { index, prepared ->
            val block = prepared.source
            key(block.id, block::class) {
                val gap = when {
                    index == 0 && firstGroup -> Dp.Hairline
                    block is ReplyBlock.Heading -> AuroraLayout.DocumentHeadingGap
                    (if (index == 0) followsHeading else blocks[index - 1].source is ReplyBlock.Heading) ->
                        AuroraLayout.Space
                    else -> AuroraLayout.ContentInset
                }
                // Text applies its own gap so long documents do not create a layout node per separator.
                val textBlock = block is ReplyBlock.Heading || block is ReplyBlock.Paragraph
                if (!textBlock && gap > Dp.Hairline) Spacer(Modifier.height(gap))
                CompositionLocalProvider(LocalReplyRevealBlock provides block.id) {
                    AuroraRichBlock(prepared, touch, if (textBlock) gap else Dp.Hairline, streaming, bodyStyle)
                }
            }
        }
    }
}

@Composable
private fun AuroraRichBlock(
    prepared: PreparedBlock,
    touch: Boolean,
    textGap: Dp,
    streaming: Boolean,
    bodyStyle: TextStyle,
) {
    val textPadding = if (textGap > Dp.Hairline) Modifier.padding(top = textGap) else Modifier
    when (val block = prepared.source) {
        is ReplyBlock.Paragraph -> AuroraReplyText(prepared.text, textPadding, style = bodyStyle)
        is ReplyBlock.Heading -> AuroraRichHeading(prepared.text, block.level, touch, bodyStyle, textPadding)
        is ReplyBlock.Quote -> Box(
            Modifier.drawBehind {
                val stroke = AuroraLayout.FrameStroke.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) stroke / 2 else size.width - stroke / 2
                drawLine(AuroraColors.MessageDivider, Offset(x, 0f), Offset(x, size.height), stroke)
            }.padding(start = AuroraLayout.ContentInset),
        ) {
            AuroraRichBlocks(prepared.children.single(), touch, streaming, bodyStyle = bodyStyle)
        }
        is ReplyBlock.ListBlock -> AuroraRichList(block, prepared.children, touch, streaming, bodyStyle)
        is ReplyBlock.Table -> AuroraRichTable(requireNotNull(prepared.table), touch)
        is ReplyBlock.Rule -> Spacer(
            Modifier.fillMaxWidth().height(AuroraLayout.Stroke).background(AuroraColors.MessageDivider),
        )
        is ReplyBlock.Code, is ReplyBlock.Diagram -> AuroraSourceBlock(block, touch, streaming, prepared.code)
    }
}

@Composable
private fun AuroraRichHeading(
    text: AnnotatedString, level: Int, touch: Boolean, body: TextStyle, modifier: Modifier = Modifier,
) {
    val (size, line) = when (level) {
        1 -> 24 to 32
        2 -> 20 to 28
        3 -> 18 to 26
        4 -> 17 to 24
        5 -> 16 to 22
        else -> 14 to 20
    }
    val factor = body.fontSize.value / if (touch) 16 else 15
    AuroraReplyText(
        text, modifier.semantics { heading() },
        body.copy(
            fontSize = (size * factor).sp,
            lineHeight = (line * factor).sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
private fun AuroraRichList(
    block: ReplyBlock.ListBlock,
    items: List<List<PreparedBlock>>,
    touch: Boolean,
    streaming: Boolean,
    bodyStyle: TextStyle,
) {
    val markerWidth = if (block.ordered) {
        maxOf(24, ((block.start.toLong() + block.items.lastIndex).toString().length + 1) * 9).dp
    } else {
        AuroraLayout.DocumentListIndent
    }
    Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.ControlGap)) {
        items.forEachIndexed { index, item ->
            val checked = block.checked.getOrNull(index)
            val marker = when {
                checked == true -> "☑"
                checked == false -> "☐"
                block.ordered -> "${block.start.toLong() + index}."
                else -> "•"
            }
            Row {
                DisableSelection {
                    val status = if (checked == null) Modifier else Modifier.semantics {
                        role = Role.Checkbox
                        toggleableState = ToggleableState(checked)
                    }
                    AuroraReplyText(marker, Modifier.widthIn(min = markerWidth).then(status), bodyStyle,
                        revealKey = "marker/$index")
                }
                Column(Modifier.weight(1f, fill = false)) {
                    AuroraRichBlocks(item, touch, streaming, bodyStyle = bodyStyle)
                }
            }
        }
    }
}
