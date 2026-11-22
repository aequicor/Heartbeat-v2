package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyAlignment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyInline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val SelectionBlockAnnotation = "AuroraSelectionBlock"

@Composable
@ReadOnlyComposable
internal fun auroraCodeFontFamily(): FontFamily = HbTypography.Code.fontFamily ?: FontFamily.Monospace

@Composable
@Suppress("InjectDispatcher") // The composition owns preparation of its styled document.
internal fun rememberPreparedDocument(document: ReplyDocument, monospace: FontFamily): PreparedDocument? {
    var ready by remember { mutableStateOf<PreparedDocument?>(null) }
    LaunchedEffect(DocumentRevision(document), monospace) {
        val previous = ready?.takeIf { it.monospace == monospace }?.blocks.orEmpty()
        ready = withContext(Dispatchers.Default) { document.prepareRichDocument(monospace, previous) }
    }
    return ready
}

private class DocumentRevision(private val document: ReplyDocument) {
    override fun equals(other: Any?) = other is DocumentRevision && document === other.document

    override fun hashCode() = 0
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
        suspend fun prepare(table: ReplyBlock.Table, monospace: FontFamily, owner: Int): PreparedTable? {
            if (table.header.isEmpty()) return null
            val context = currentCoroutineContext()
            val header = table.header.map { prepareText(it, monospace, owner) }
            val widths = header.map { it.length }.toMutableList()
            val rows = table.rows.map { row ->
                context.ensureActive()
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

internal suspend fun ReplyDocument.prepareRichDocument(
    monospace: FontFamily,
    previous: List<PreparedBlock> = listOf(),
): PreparedDocument = PreparedDocument(monospace, prepareBlocks(blocks, previous, monospace))

private suspend fun prepareBlocks(
    blocks: List<ReplyBlock>,
    previous: List<PreparedBlock>,
    monospace: FontFamily,
    selectionOwner: Int? = null,
): List<PreparedBlock> {
    val context = currentCoroutineContext()
    val byId by lazy(LazyThreadSafetyMode.NONE) { previous.associateBy { it.source.id } }
    return blocks.mapIndexedNotNull { index, block ->
        context.ensureActive()
        val prior = previous.getOrNull(index)?.takeIf { it.source.id == block.id } ?: byId[block.id]
        if (prior?.source === block) return@mapIndexedNotNull prior
        val owner = selectionOwner ?: block.id
        when (block) {
            is ReplyBlock.Paragraph -> {
                val text = buildAnnotatedString {
                    block.lines.forEachIndexed { lineIndex, spans ->
                        context.ensureActive()
                        if (lineIndex > 0) append('\n')
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
            is ReplyBlock.ListBlock -> PreparedBlock(block, children = block.items.mapIndexed { itemIndex, item ->
                prepareBlocks(item, prior?.children?.getOrNull(itemIndex).orEmpty(), monospace, owner)
            })
            is ReplyBlock.Table -> PreparedTable.prepare(block, monospace, owner)?.let {
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
