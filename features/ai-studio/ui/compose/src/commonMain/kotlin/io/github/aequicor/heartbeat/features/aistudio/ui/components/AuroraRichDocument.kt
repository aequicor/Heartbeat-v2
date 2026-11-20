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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyAlignment
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyInline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive

private const val SelectionBlockAnnotation = "AuroraSelectionBlock"

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
            BasicText(plain, body, style = auroraMessageStyle(), onTextLayout = onTextLayout)
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
            fallbackText.isNotEmpty() -> BasicText(fallbackText, style = auroraMessageStyle())
            preparingHeight > Dp.Hairline -> AuroraPreparingReply(minHeight = preparingHeight)
        }
    }
}

internal class PreparedDocument(val monospace: FontFamily, val blocks: List<PreparedBlock>) {
    fun throughSelection(selected: List<AnnotatedString>): PreparedDocument {
        var lastSelectedId = Int.MIN_VALUE
        for (fragment in selected) {
            val id = fragment.getStringAnnotations(SelectionBlockAnnotation, 0, fragment.length)
                .firstOrNull()?.item?.toIntOrNull() ?: return this
            lastSelectedId = maxOf(lastSelectedId, id)
        }
        val prefix = blocks.takeWhile { it.source.id <= lastSelectedId }
        return if (prefix.isEmpty()) this else PreparedDocument(monospace, prefix)
    }

    fun appendNewBlocks(latest: PreparedDocument?): PreparedDocument {
        val lastPinnedId = blocks.lastOrNull()?.source?.id
        // Keep the existing section under selection. Only independently addressable blocks follow it.
        val newBlocks = if (latest != null && latest.monospace == monospace && lastPinnedId != null) {
            latest.blocks.dropWhile { it.source.id <= lastPinnedId }
        } else listOf()
        return if (newBlocks.isEmpty()) this else PreparedDocument(monospace, blocks + newBlocks)
    }
}

@Composable
internal fun selectedPreparedDocument(latest: PreparedDocument?, selection: SelectionState): PreparedDocument? {
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

internal class PreparedBlock(
    val source: ReplyBlock,
    val text: AnnotatedString = AnnotatedString(""),
    val children: List<List<PreparedBlock>> = listOf(),
    val table: PreparedTable? = null,
    val code: AuroraPreparedCode? = null,
) {
    val hasOwnViewport: Boolean =
        source is ReplyBlock.Code || source is ReplyBlock.Diagram || source is ReplyBlock.Table
}

internal class PreparedTable(
    val header: List<AnnotatedString>,
    val rows: List<List<AnnotatedString>>,
    val alignments: List<ReplyAlignment>,
    val columnCharacters: List<Int>,
) {
    fun clipboardText() = (listOf(header) + rows).joinToString("\n") { row ->
        row.joinToString("\t") { it.text }
    }

    companion object {
        fun prepare(scope: CoroutineScope, table: ReplyBlock.Table, monospace: FontFamily, owner: Int): PreparedTable? {
            if (table.header.isEmpty()) return null
            val header = table.header.map { prepareText(it, monospace, owner) }
            val widths = header.map { it.length }.toMutableList()
            val rows = table.rows.map { row ->
                scope.ensureActive()
                row.mapIndexed { index, spans ->
                    prepareText(spans, monospace, owner).also {
                        if (index < widths.size) widths[index] = maxOf(widths[index], it.length)
                    }
                }
            }
            return PreparedTable(header, rows, table.alignments, widths)
        }
    }
}

internal suspend fun CoroutineScope.prepareBlocks(
    blocks: List<ReplyBlock>,
    previous: List<PreparedBlock>,
    monospace: FontFamily,
    selectionOwner: Int? = null,
): List<PreparedBlock> {
    val byId by lazy(LazyThreadSafetyMode.NONE) { previous.associateBy { it.source.id } }
    return blocks.mapIndexedNotNull { index, block ->
        ensureActive()
        val prior = previous.getOrNull(index)?.takeIf { it.source.id == block.id } ?: byId[block.id]
        if (prior?.source === block) return@mapIndexedNotNull prior
        val owner = selectionOwner ?: block.id
        when (block) {
            is ReplyBlock.Paragraph -> {
                val text = buildAnnotatedString {
                    block.lines.forEachIndexed { index, spans ->
                        ensureActive()
                        if (index > 0) append('\n')
                        appendReplySpans(spans, monospace)
                    }
                    addStringAnnotation(SelectionBlockAnnotation, owner.toString(), 0, length)
                }
                if (text.isEmpty()) null else PreparedBlock(block, text = text)
            }
            is ReplyBlock.Heading -> PreparedBlock(block, text = prepareText(block.text, monospace, owner))
            is ReplyBlock.Quote -> PreparedBlock(
                block,
                children = listOf(
                    prepareBlocks(block.blocks, prior?.children?.firstOrNull().orEmpty(), monospace, owner),
                ),
            )
            is ReplyBlock.ListBlock -> PreparedBlock(block, children = block.items.mapIndexed { index, item ->
                prepareBlocks(item, prior?.children?.getOrNull(index).orEmpty(), monospace, owner)
            })
            is ReplyBlock.Table -> PreparedTable.prepare(this, block, monospace, owner)?.let {
                PreparedBlock(block, table = it)
            }
            is ReplyBlock.Code -> PreparedBlock(block, code = prepareAuroraCode(block.source, block.lines, prior?.code))
            else -> PreparedBlock(block)
        }
    }
}

private fun prepareText(spans: List<ReplyInline>, monospace: FontFamily, owner: Int): AnnotatedString =
    buildAnnotatedString {
        appendReplySpans(spans, monospace)
        addStringAnnotation(SelectionBlockAnnotation, owner.toString(), 0, length)
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
                AuroraRichBlock(prepared, touch, if (textBlock) gap else Dp.Hairline, streaming, bodyStyle)
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
        is ReplyBlock.Paragraph -> BasicText(prepared.text, textPadding, style = bodyStyle)
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
    BasicText(
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
                    BasicText(marker, Modifier.widthIn(min = markerWidth).then(status), bodyStyle)
                }
                Column(Modifier.weight(1f, fill = false)) {
                    AuroraRichBlocks(item, touch, streaming, bodyStyle = bodyStyle)
                }
            }
        }
    }
}

private val safeLinkPrefixes = listOf("https://", "http://", "mailto:")

private fun AnnotatedString.Builder.appendReplySpans(spans: List<ReplyInline>, monospace: FontFamily) {
    for (span in spans) {
        val emphasis = span.bold || span.italic || span.strike
        if (!emphasis && !span.code && span.url == null) {
            append(span.text)
            continue
        }
        val url = span.url?.takeIf { candidate ->
            !candidate.any(Char::isISOControl) && safeLinkPrefixes.any { candidate.startsWith(it, ignoreCase = true) }
        }
        withStyle(span.textStyle(monospace)) {
            if (url == null) append(span.text) else withLink(linkForSpan(url, span.strike)) { append(span.text) }
        }
    }
}

private fun ReplyInline.textStyle(monospace: FontFamily) = SpanStyle(
    fontWeight = if (bold) FontWeight.SemiBold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    fontFamily = if (code) monospace else null,
    background = if (code) AuroraColors.WorkHover else Color.Unspecified,
    textDecoration = if (strike) TextDecoration.LineThrough else null,
)

private fun linkForSpan(url: String, strike: Boolean): LinkAnnotation.Url {
    val underline = if (strike) {
        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    } else {
        TextDecoration.Underline
    }
    return LinkAnnotation.Url(
        url,
        TextLinkStyles(
            style = SpanStyle(color = AuroraColors.DesktopPrimaryAction, textDecoration = underline),
            focusedStyle = SpanStyle(background = AuroraColors.Lavender),
            hoveredStyle = SpanStyle(background = AuroraColors.WorkHover),
        ),
    )
}
