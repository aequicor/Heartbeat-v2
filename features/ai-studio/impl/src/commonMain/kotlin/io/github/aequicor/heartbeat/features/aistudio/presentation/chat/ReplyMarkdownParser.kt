package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyBlock

import kotlinx.coroutines.CancellationException
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.EmptyStreamingMarkdownFile
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

/** Incremental parser for a single reply. Use it from one worker at a time. */
internal class ReplyMarkdownParser(private val literalFastPath: Boolean = true) {
    private var stream = EmptyStreamingMarkdownFile()
    private var previousSource: String = ""
    private var previousNormalizedLength: Int = 0
    private var stableCount: Int = 0
    private val stableBlocks = mutableListOf<ReplyBlock>()
    private val stableLinks = mutableMapOf<CharSequence, LinkMap.LinkInfo>()
    private var linkDestinations = mapOf<CharSequence, CharSequence>()
    private var openFence: OpenReplyFence? = null

    fun renderReadyText(id: String, source: String): LiveText =
        LiveText(id, source, parseContent(source, final = false))

    fun parse(source: String, final: Boolean): ReplyDocument =
        parseContent(source, final) ?: ReplyDocument.plain(source)

    // Null keeps ordinary text on the native selection path. On parser failure retain the source.
    @Suppress("TooGenericExceptionCaught")
    private fun parseContent(source: String, final: Boolean): ReplyDocument? = try {
        if (final || !source.startsWith(previousSource)) reset()

        when {
            source.isEmpty() || (literalFastPath && source.isLiteralReply()) -> null
            final -> parseFinal(checkedSource(source))
            else -> parseStreamingContent(source)
        }
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        reset()
        ReplyDocument.plain(source)
    }

    private fun parseStreamingContent(source: String): ReplyDocument {
        val preview = openFence?.advance(source)
        if (preview != null) {
            previousSource = source
            return preview
        }
        openFence = null
        return parseStream(source, checkedSource(source.removeSuffix("\r")))
    }

    private fun checkedSource(source: String): MarkdownSource = MarkdownSource(source).also { text ->
        val changedLineStart = text.normalized.lastIndexOf('\n', previousNormalizedLength - 1) + 1
        text.normalized.checkQuoteNesting(changedLineStart)
    }

    private fun parseFinal(text: MarkdownSource): ReplyDocument {
        val root = MarkdownParser(GFMFlavourDescriptor(), false, CancellationToken.NonCancellable)
            .buildMarkdownTreeFromString(text.normalized as CharSequence)
        root.children.checkDepth()
        val reader = ReplyMarkdownReader(text, LinkMap.buildLinkMap(root, text.normalized), true)
        return ReplyDocument(reader.blocks(root.children))
    }

    private fun parseStream(source: String, text: MarkdownSource): ReplyDocument {
        val normalized = text.normalized
        stream.append(normalized.subSequence(previousNormalizedLength, normalized.length))
        previousSource = source
        previousNormalizedLength = normalized.length
        val stable = stream.stableChildren
        val newStable = stable.subList(stableCount, stable.size)
        val tail = stream.unstableTail
        newStable.checkDepth()
        tail.checkDepth()
        val definitions = if ("]:" in normalized) {
            text.collectLinks(newStable, stableLinks)
            stableLinks.toMutableMap().also { text.collectLinks(tail, it) }
        } else mapOf()
        val reader = ReplyMarkdownReader(text, LinkMap(definitions), false)
        val destinations = definitions.mapValues { it.value.destination }
        // A late definition can change an earlier reference without changing its syntax tree.
        if (destinations == linkDestinations) {
            stableBlocks += reader.blocks(newStable)
        } else {
            stableBlocks.clear()
            stableBlocks += reader.blocks(stable)
            linkDestinations = destinations
        }
        stableCount = stable.size
        val blocks = stableBlocks + reader.blocks(tail)
        val fence = text.openFence(tail.lastOrNull(), blocks)
        val preview = fence?.advance(source)
        if (preview != null) openFence = fence
        return preview ?: ReplyDocument(blocks)
    }

    private fun reset() {
        stream = EmptyStreamingMarkdownFile()
        previousSource = ""
        previousNormalizedLength = 0
        stableCount = 0
        stableBlocks.clear()
        stableLinks.clear()
        linkDestinations = mapOf()
        openFence = null
    }
}

/** Plain replies retain their original text and native selection. */
internal fun String.isLiteralReply(): Boolean =
    isEmpty() || (
        !first().isWhitespace() && !first().isDigit() && !startsWith('-') && !startsWith('+') &&
            none { it in MarkdownPunctuation } && "www." !in this && "://" !in this &&
            !lineSequence().drop(1).any { line ->
                line.startsWith(' ') || line.startsWith('-') || line.startsWith('+') ||
                    line.firstOrNull()?.isDigit() == true
            }
        )

private const val MarkdownPunctuation = "\r\t#*>|_`~[<\\&=@"

private fun List<ASTNode>.checkDepth(remaining: Int = MaxTreeDepth) {
    if (isEmpty()) return
    require(remaining > 0) { "Markdown is nested too deeply" }
    for (node in this) node.children.checkDepth(remaining - 1)
}

private const val MaxTreeDepth = 128
private val deepQuotePrefix = Regex("^(?: {0,3}>){${MaxTreeDepth + 1}}", RegexOption.MULTILINE)
private val fenceLine = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")

private fun String.checkQuoteNesting(changedLineStart: Int) {
    if (deepQuotePrefix.find(this, changedLineStart) == null) return
    // Each nested quote allocates a larger constraints array inside JetBrains Markdown.
    // Check the prefix before parsing, while preserving literal quote data in fenced snippets.
    var opener: String? = null
    for (line in lineSequence()) {
        val fence = fenceLine.matchEntire(line)
        val marker = fence?.groupValues?.get(1)
        val suffix = fence?.groupValues?.get(2).orEmpty()
        val opening = opener
        if (opening == null) {
            require(!deepQuotePrefix.containsMatchIn(line)) { "Markdown quote is nested too deeply" }
            if (marker != null && (marker.first() == '~' || '`' !in suffix)) opener = marker
        } else if (marker != null && marker.closesFence(opening, suffix)) {
            opener = null
        }
    }
}

private fun String.closesFence(opening: String, suffix: String): Boolean =
    first() == opening.first() && length >= opening.length && suffix.all { it == ' ' || it == '\t' }

private fun MarkdownSource.openFence(node: ASTNode?, blocks: List<ReplyBlock>): OpenReplyFence? {
    if (node?.type != MarkdownElementTypes.CODE_FENCE || node.endOffset != normalized.length ||
        node.children.any { it.type == MarkdownTokenTypes.CODE_FENCE_END }
    ) return null
    val block = when (val last = blocks.lastOrNull()) {
        is ReplyBlock.Code, is ReplyBlock.Diagram -> last.takeIf { it.id == originalOffset(node.startOffset) }
        else -> null
    }
    val opener = node.children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_START }
        ?.let(::text).orEmpty()
    // For nested or indented fences, the Markdown parser strips container markers and indentation.
    val marker = opener.firstOrNull()
    val newline = node.children.firstOrNull { it.type == MarkdownTokenTypes.EOL }
    val unindented = marker != null && marker in "`~" && opener.all { it == marker }
    return if (block != null && newline != null && unindented) {
        OpenReplyFence(opener, originalOffset(newline.endOffset), blocks.dropLast(1), block)
    } else null
}

private fun MarkdownSource.collectLinks(nodes: List<ASTNode>, definitions: MutableMap<CharSequence, LinkMap.LinkInfo>) {
    fun collect(node: ASTNode) {
        if (node.type == MarkdownElementTypes.LINK_DEFINITION) {
            val label = node.children.first { it.type == MarkdownElementTypes.LINK_LABEL }
            definitions.getOrPut(LinkMap.normalizeLabel(text(label))) {
                LinkMap.LinkInfo.create(node, normalized)
            }
        } else {
            node.children.forEach(::collect)
        }
    }
    nodes.forEach(::collect)
}

internal class MarkdownSource(private val original: String) {
    private val crlf = mutableListOf<Int>()
    val normalized: String = if ('\r' !in original) original else buildString {
        var index = 0
        while (index < original.length) {
            val char = original[index++]
            if (char != '\r') {
                append(char)
            } else {
                if (original.getOrNull(index) == '\n') {
                    crlf += length
                    index++
                }
                append('\n')
            }
        }
    }

    fun originalOffset(offset: Int): Int {
        val index = crlf.binarySearch(offset)
        return offset + if (index < 0) -index - 1 else index
    }

    fun originalText(start: Int, end: Int): String =
        original.substring(originalOffset(start), originalOffset(end))

    fun text(node: ASTNode): String = normalized.substring(node.startOffset, node.endOffset)

    fun raw(node: ASTNode): String = originalText(node.startOffset, node.endOffset)
}
