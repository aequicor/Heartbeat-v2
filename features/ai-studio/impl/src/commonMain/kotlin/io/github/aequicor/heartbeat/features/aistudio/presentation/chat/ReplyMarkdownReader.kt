package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyInline
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyAlignment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyDiagramKind

import org.intellij.markdown.MarkdownElementTypes as Element
import org.intellij.markdown.MarkdownTokenTypes as Token
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes as GfmElement
import org.intellij.markdown.flavours.gfm.GFMTokenTypes as GfmToken
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap

internal class ReplyMarkdownReader(
    private val source: MarkdownSource,
    private val links: LinkMap,
    private val final: Boolean,
) {
    fun blocks(nodes: List<ASTNode>): List<ReplyBlock> = nodes.mapNotNull(::block)

    private fun block(node: ASTNode): ReplyBlock? {
        val id = source.originalOffset(node.startOffset)
        return when (node.type) {
            Element.PARAGRAPH -> ReplyBlock.Paragraph(id, node.children.flatMap(::inline).lines())
            in headingLevels -> {
                val text = node.children.firstOrNull { it.type == Token.ATX_CONTENT || it.type == Token.SETEXT_CONTENT }
                val content = text?.children.orEmpty().flatMap(::inline).trim()
                ReplyBlock.Heading(id, headingLevels.getValue(node.type), content)
            }
            Element.BLOCK_QUOTE, GfmElement.ALERT -> ReplyBlock.Quote(id, blocks(node.children))
            Element.ORDERED_LIST, Element.UNORDERED_LIST -> readList(id, node)
            GfmElement.TABLE -> readTable(id, node)
            Element.CODE_FENCE, Element.CODE_BLOCK -> readCode(id, node)
            Token.HORIZONTAL_RULE -> ReplyBlock.Rule(id)
            Element.LINK_DEFINITION, Token.BLOCK_QUOTE, GfmToken.ALERT_TITLE -> null
            else -> source.text(node).takeUnless(String::isBlank)?.let { literal ->
                ReplyBlock.Paragraph(id, listOf(ReplyInline(literal)).lines())
            }
        }
    }

    private fun readList(id: Int, node: ASTNode): ReplyBlock.ListBlock {
        val ordered = node.type == Element.ORDERED_LIST
        val items = node.children.filter { it.type == Element.LIST_ITEM }
        val start = items.firstOrNull()?.children?.firstOrNull { it.type == Token.LIST_NUMBER }
            ?.let(source::text)?.trimStart()?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 1
        val checked = items.map { item ->
            item.children.firstOrNull { it.type == GfmToken.CHECK_BOX }?.let { 'x' in source.text(it).lowercase() }
        }
        return ReplyBlock.ListBlock(id, ordered, start, items.map { item ->
            blocks(item.children.filterNot { it.type in listMarkers })
        }, checked)
    }

    private fun readTable(id: Int, node: ASTNode): ReplyBlock.Table {
        fun cells(row: ASTNode) = row.children.filter { it.type == GfmToken.CELL }
            .map { it.children.flatMap(::inline).trim() }

        val header = node.children.firstOrNull { it.type == GfmElement.HEADER }?.let(::cells).orEmpty()
        val separator = node.children.firstOrNull { it.type == GfmToken.TABLE_SEPARATOR }?.let(source::text).orEmpty()
        val markers = separator.trim().removePrefix("|").removeSuffix("|").split('|').map(String::trim)
        val alignments = List(header.size) { index ->
            val marker = markers.getOrNull(index).orEmpty()
            when {
                marker.startsWith(':') && marker.endsWith(':') -> ReplyAlignment.Center
                marker.endsWith(':') -> ReplyAlignment.Right
                else -> ReplyAlignment.Left
            }
        }
        val rows = node.children.filter { it.type == GfmElement.ROW }.map(::cells).map { row ->
            List(header.size) { index -> row.getOrNull(index).orEmpty() }
        }
        return ReplyBlock.Table(id, header, rows, alignments)
    }

    private fun readCode(id: Int, node: ASTNode): ReplyBlock {
        val opening = node.children.firstOrNull { it.type == Token.CODE_FENCE_START }
        val language = node.children.firstOrNull { it.type == Token.FENCE_LANG }?.let(source::text)
            ?.trim()?.takeWhile { !it.isWhitespace() }?.lowercase()?.ifEmpty { null }
        val ending = node.children.firstOrNull { it.type == Token.CODE_FENCE_END }
        val closed = ending != null && (final || source.normalized.getOrNull(ending.endOffset) == '\n')
        val indent = opening?.let { source.text(it).takeWhile { char -> char == ' ' }.length } ?: IndentedCodeWidth
        val content = codeContent(node, opening == null, indent)
        val diagram = when (language) {
            "mermaid", "mmd" -> ReplyDiagramKind.Mermaid
            "plantuml", "puml", "uml" -> ReplyDiagramKind.PlantUml
            else -> null
        }
        return if (diagram != null) ReplyBlock.Diagram(id, diagram, content, closed)
        else ReplyBlock.Code(id, language, content, highlightReplyCode(content, language), opening == null || closed)
    }

    private fun codeContent(node: ASTNode, indented: Boolean, indent: Int): String = buildString {
        var body = indented
        for (child in node.children) {
            when (child.type) {
                Token.EOL -> if (body) append(source.raw(child)) else body = true
                Token.CODE_LINE, Token.CODE_FENCE_CONTENT -> if (body) {
                    val text = source.raw(child)
                    val leading = text.takeWhile { it == ' ' }.length
                    append(if (indented && text.startsWith('\t')) text.drop(1) else text.drop(minOf(leading, indent)))
                }
            }
        }
        if (indented && source.normalized.getOrNull(node.endOffset) == '\n') {
            append(source.originalText(node.endOffset, node.endOffset + 1))
        }
    }

    private fun inline(node: ASTNode): List<ReplyInline> {
        val text = source.text(node)
        fun formatted(markerCount: Int, apply: (ReplyInline) -> ReplyInline) =
            node.children.drop(markerCount).dropLast(markerCount).flatMap(::inline).map(apply)

        return when (node.type) {
            Element.EMPH -> formatted(1) { it.copy(italic = true) }
            Element.STRONG -> formatted(2) { it.copy(bold = true) }
            GfmElement.STRIKETHROUGH -> formatted(text.takeWhile { it == '~' }.length) { it.copy(strike = true) }
            Element.CODE_SPAN -> listOf(inlineCode(node))
            Element.INLINE_LINK, Element.SHORT_REFERENCE_LINK, Element.FULL_REFERENCE_LINK -> link(node)
            Element.IMAGE -> imageText(node, text)
            Element.AUTOLINK, GfmToken.GFM_AUTOLINK -> listOf(autolink(text))
            Token.BLOCK_QUOTE, Token.HARD_LINE_BREAK -> listOf()
            Token.HTML_TAG -> listOf(ReplyInline(text))
            else -> listOf(ReplyInline(plainInlineText(text)))
        }
    }

    private fun inlineCode(node: ASTNode): ReplyInline {
        val code = node.children.drop(1).dropLast(1).joinToString("") { source.text(it) }.replace('\n', ' ')
        val value = if (code.startsWith(' ') && code.endsWith(' ') && code.any { it != ' ' }) {
            code.drop(1).dropLast(1)
        } else code
        return ReplyInline(value, code = true)
    }

    private fun imageText(node: ASTNode, literal: String): List<ReplyInline> {
        val target = node.children.firstOrNull { it.type in linkTypes }
        val label = target?.children?.firstOrNull { it.type == Element.LINK_TEXT || it.type == Element.LINK_LABEL }
        return if (label == null) listOf(ReplyInline(literal)) else label.children.drop(1).dropLast(1).flatMap(::inline)
    }

    private fun autolink(text: String): ReplyInline {
        val display = text.removeSurrounding("<", ">")
        val destination = when {
            display.startsWith("www.", ignoreCase = true) -> "https://$display"
            ':' !in display && '@' in display -> "mailto:$display"
            else -> display
        }
        return ReplyInline(display, url = destination.safeLink())
    }

    private fun link(node: ASTNode): List<ReplyInline> {
        val label = node.children.firstOrNull { it.type == Element.LINK_LABEL }
        val display = node.children.firstOrNull { it.type == Element.LINK_TEXT } ?: label
        val destination = if (node.type == Element.INLINE_LINK) {
            node.children.firstOrNull { it.type == Element.LINK_DESTINATION }?.let(source::text)
                ?.let { LinkMap.normalizeDestination(it, true).toString() }.orEmpty()
        } else {
            val reference = label ?: display
            reference?.let { links.getLinkInfo(source.text(it))?.destination?.toString() }
                ?: return listOf(ReplyInline(source.text(node)))
        }
        return display?.children.orEmpty().drop(1).dropLast(1).flatMap(::inline)
            .map { it.copy(url = destination.safeLink()) }
    }
}

private const val IndentedCodeWidth = 4

private val headingLevels = mapOf(
    Element.ATX_1 to 1, Element.ATX_2 to 2, Element.ATX_3 to 3,
    Element.ATX_4 to 4, Element.ATX_5 to 5, Element.ATX_6 to 6,
    Element.SETEXT_1 to 1, Element.SETEXT_2 to 2,
)

private val listMarkers = setOf(Token.LIST_BULLET, Token.LIST_NUMBER, GfmToken.CHECK_BOX)
private val linkTypes = setOf(Element.INLINE_LINK, Element.SHORT_REFERENCE_LINK, Element.FULL_REFERENCE_LINK)
private val escapedHtmlCharacter = Regex("&(?:amp|quot|lt|gt);")
private val htmlCharacters = mapOf("&amp;" to "&", "&quot;" to "\"", "&lt;" to "<", "&gt;" to ">")

private fun plainInlineText(text: String): String =
    escapedHtmlCharacter.replace(EntityConverter.replaceEntities(text, true, true)) { htmlCharacters.getValue(it.value) }

private fun String.safeLink(): String? = takeIf {
    val scheme = substringBefore(':').lowercase()
    scheme in setOf("http", "https", "mailto") && ':' in this && none { char -> char.isISOControl() }
}

private fun List<ReplyInline>.lines(): List<List<ReplyInline>> = buildList {
    val current = mutableListOf<ReplyInline>()
    for (run in this@lines) {
        run.text.split('\n').forEachIndexed { index, part ->
            if (index > 0) {
                add(current.trim())
                current.clear()
            }
            if (part.isNotEmpty()) current.add(run.copy(text = part))
        }
    }
    add(current.trim())
}

private fun List<ReplyInline>.trim(): List<ReplyInline> {
    val first = indexOfFirst { it.text.isNotBlank() || it.code }
    if (first < 0) return listOf()
    val last = indexOfLast { it.text.isNotBlank() || it.code }
    return subList(first, last + 1).mapIndexed { index, run ->
        if (run.code) return@mapIndexed run
        val text = when {
            first == last -> run.text.trim()
            index == 0 -> run.text.trimStart()
            index == last - first -> run.text.trimEnd()
            else -> run.text
        }
        run.copy(text = text)
    }
}
