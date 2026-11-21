package io.github.aequicor.heartbeat.features.aistudio.presentation.rich

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeHighlight
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyAlignment
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDiagramKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReplyMarkdownParserTest {
    @Test
    fun livePreviewHoldsOnlyTheUnfinishedSyntaxAndFinalKeepsEverything() {
        val examples = listOf(
            "[пример](https://example.com)", "**выделение**", "_курсив_", "~~удалено~~",
            "`код`", "![подпись](image.png)", "&amp;", "<https://example.com>",
        )
        for (syntax in examples) {
            val parser = ReplyMarkdownParser()
            for (length in 1 until syntax.length) {
                val input = "См. " + syntax.take(length)
                assertEquals("См. ", parser.renderLiveText("reply", input).text, input)
                assertEquals(ReplyMarkdownParser().parse(input, final = true), parser.parse(input, final = true))
            }
            val complete = "См. $syntax "
            assertEquals(complete, parser.renderLiveText("reply", complete).text)
        }
        val parser = ReplyMarkdownParser()
        val ready = parser.renderLiveText("reply", "См. [пример](https://example.com)")
        assertEquals("https://example.com", assertIs<ReplyBlock.Paragraph>(ready.document?.blocks?.single())
            .lines.single().last().url)
        assertEquals("literal \\* and snake_case ", readyReplyMarkdown("literal \\* and snake_case "))
        assertEquals("До\n\n```kotlin\nval x = \"[**\"", readyReplyMarkdown("До\n\n```kotlin\nval x = \"[**\""))
        assertEquals("До\r\n", readyReplyMarkdown("До\r\n```kot"))
    }

    @Test
    fun liveTextDistinguishesLiteralSourceFromMarkdownAndParserFallback() {
        val parser = ReplyMarkdownParser()
        val plain = parser.renderLiveText("reply", "Review")
        assertEquals("reply", plain.replyId)
        assertEquals("Review", plain.text)
        assertNull(plain.document)

        val markdown = parser.renderLiveText("reply", "Review **complete**")
        val paragraph = assertIs<ReplyBlock.Paragraph>(markdown.document?.blocks?.single())
        assertTrue(paragraph.lines.single().last().bold)
        assertNull(parser.renderLiveText("reply", "Replacement").document)

        val rejected = "> ".repeat(129) + "Review"
        assertEquals(rejected, parser.renderLiveText("reply", rejected).document?.plainText)
    }

    @Test
    fun streamingReusesStableBlocksAndReplacesTheProvisionalTail() {
        val parser = ReplyMarkdownParser()
        val partial = parser.parse("# Review\n\nText\n\n``", final = false)
        val open = parser.parse("# Review\n\nText\n\n```kotlin\nval x = 2", final = false)

        assertSame(partial.blocks[0], open.blocks[0])
        assertSame(partial.blocks[1], open.blocks[1])
        val code = assertIs<ReplyBlock.Code>(open.blocks.last())
        assertEquals(partial.blocks.last().id, code.id)
        assertEquals("val x = 2", code.source)
        assertFalse(code.closed)
        assertEquals(
            listOf(ReplyCodeHighlight(0, 3, ReplyCodeKind.Keyword), ReplyCodeHighlight(8, 9, ReplyCodeKind.Number)),
            code.lines.single().highlights,
        )

        val final = parser.parse("Replacement response.", final = true)
        assertEquals(ReplyDocument.plain("Replacement response."), final)
    }

    @Test
    fun rendersClosedDiagramsWithOriginalLineEndingsAndStableOffsets() {
        val parser = ReplyMarkdownParser()
        val source = "Before\r\n\r\n~~~Mermaid\r\nflowchart LR\r\nA --> B\r\n~~~"
        val partial = parser.parse(source.substringBeforeLast("\n") + "\n~~", final = false)
        val provisional = parser.parse(source, final = false)
        val confirmed = parser.parse(source + "\r\n", final = false)
        val code = assertIs<ReplyBlock.Diagram>(confirmed.blocks.last())
        assertEquals(ReplyDiagramKind.Mermaid, code.kind)
        assertEquals(source.indexOf("~~~"), code.id)
        assertEquals(partial.blocks.last().id, code.id)
        assertFalse(assertIs<ReplyBlock.Diagram>(provisional.blocks.last()).closed)
        assertTrue(code.closed)
        assertEquals("flowchart LR\r\nA --> B\r\n", code.source)

        val unclosed = assertIs<ReplyBlock.Diagram>(
            parser.parse("```puml\n@startuml\nA -> B", final = true).blocks.single(),
        )
        assertEquals(ReplyDiagramKind.PlantUml, unclosed.kind)
        assertFalse(unclosed.closed)
    }

    @Test
    fun mapsGitHubBlocksAndInlineStyles() {
        val source = """
            # **Title** and *emphasis*

            > Quoted ~~draft~~ with `code`

            3. [x] Done
            4. [ ] Next
               - nested

            | Name | State |
            | :--- | :---: |
            | A | **ready** |
            | B |

            ---

            [Read](https://example.com) [Unsafe](file:///tmp/item)
        """.trimIndent()
        val blocks = ReplyMarkdownParser().parse(source, final = true).blocks
        val heading = assertIs<ReplyBlock.Heading>(blocks[0])
        assertEquals(1, heading.level)
        assertEquals("Title", heading.text.single { it.bold }.text)
        assertEquals("emphasis", heading.text.single { it.italic }.text)

        val quote = assertIs<ReplyBlock.Quote>(blocks[1])
        val line = assertIs<ReplyBlock.Paragraph>(quote.blocks.single()).lines.single()
        assertEquals("draft", line.single { it.strike }.text)
        assertEquals("code", line.single { it.code }.text)

        val list = assertIs<ReplyBlock.ListBlock>(blocks[2])
        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertEquals(listOf(true, false), list.checked)
        assertIs<ReplyBlock.ListBlock>(list.items[1].last())

        val table = assertIs<ReplyBlock.Table>(blocks[3])
        assertEquals(listOf(ReplyAlignment.Left, ReplyAlignment.Center), table.alignments)
        assertTrue(table.rows[0][1].single().bold)
        assertTrue(table.rows[1][1].isEmpty())
        assertIs<ReplyBlock.Rule>(blocks[4])

        val links = assertIs<ReplyBlock.Paragraph>(blocks[5]).lines.single().filter { it.text.isNotBlank() }
        assertEquals("https://example.com", links[0].url)
        assertEquals("Unsafe", links[1].text)
        assertNull(links[1].url)
    }

    @Test
    fun lateReferenceDefinitionUpdatesAnEarlierParagraph() {
        val parser = ReplyMarkdownParser()
        val first = "[Read][ref]\n\n# Next\n\n"
        val unresolved = assertIs<ReplyBlock.Paragraph>(parser.parse(first, final = false).blocks.first())
        val text = first + "[ref]: https://example.com/docs\n"
        val resolved = assertIs<ReplyBlock.Paragraph>(parser.parse(text, final = false).blocks.first())
        assertEquals(unresolved.id, resolved.id)
        assertNull(unresolved.lines.single().single().url)
        assertEquals("https://example.com/docs", resolved.lines.single().single().url)
        assertEquals(resolved, parser.parse(text, final = true).blocks.first())
    }

    @Test
    fun splittingAcrossEveryBoundaryMatchesTheFinalDocument() {
        val source = "# Title\r\n\r\n> **A** and *B*\r\n\r\n" +
            "| a | b |\r\n| - | - |\r\n| x | y |\r\n\r\n" +
            "~~~puml\r\n@startuml\r\nA -> B\r\n@enduml\r\n~~~\r\n\r\n" +
            "3. [x] first\r\n4. second\r\n\r\n[Docs][docs]\r\n\r\n[docs]: https://example.com\r\n"
        val parser = ReplyMarkdownParser()
        for (length in 1 until source.length) parser.parse(source.take(length), final = false)
        val streamed = parser.parse(source, final = false)
        assertEquals(parser.parse(source, final = true), streamed)
    }

    @Test
    fun growingFenceKeepsPreviouslyRenderedLinesAndResolvesAClosingCandidate() {
        val parser = ReplyMarkdownParser()
        val prefix = "# Result\r\n\r\n```diff\r\n-old\r\n```\r\n\r\n````kotlin\r\n"
        val firstLine = "/* started\r\n"
        val initial = parser.parse(prefix + firstLine + "pending\r", final = false)
        val code = assertIs<ReplyBlock.Code>(initial.blocks.last())
        assertEquals(listOf("/* started", "pending"), code.lines.map { it.text })

        val body = firstLine + "pending\r\n" + "inside the comment\r\n".repeat(1024) + "*/ val last = 1\r\n"
        val grown = parser.parse(prefix + body + "```", final = false)
        val preview = assertIs<ReplyBlock.Code>(grown.blocks.last())
        assertSame(initial.blocks[1], grown.blocks[1])
        assertSame(code.lines.first(), preview.lines.first())
        assertFalse(preview.closed)
        assertEquals(1028, preview.lines.size)
        assertEquals(ReplyCodeKind.Comment, preview.lines[2].highlights.single().kind)
        assertEquals("pending", code.lines.last().text)

        val ambiguous = parser.parse(prefix + body + "````", final = false)
        assertFalse(assertIs<ReplyBlock.Code>(ambiguous.blocks.last()).closed)
        val replacement = prefix + body + "```` is code\r\n````\r\n\r\n**after**"
        val closed = parser.parse(replacement, final = false)
        assertTrue(assertIs<ReplyBlock.Code>(closed.blocks[2]).closed)
        assertEquals(parser.parse(replacement, final = true), closed)
    }

    @Test
    fun longStringWithEscapesStaysOneHighlight() {
        val quoted = "\"" + "\\\"".repeat(12_000) + "\""
        val result = ReplyMarkdownParser().parse("```kotlin\nval s = $quoted\n```\n", final = true)
        val code = assertIs<ReplyBlock.Code>(result.blocks.single())
        assertEquals(
            ReplyCodeHighlight(8, 8 + quoted.length, ReplyCodeKind.String),
            code.lines.single().highlights.last(),
        )
    }

    @Test
    fun deeplyNestedMarkdownFallsBackAndTheParserRecoversForTheNextSnapshot() {
        val parser = ReplyMarkdownParser()
        val source = "> ".repeat(16_384) + "Deep quote"
        assertEquals(source, parser.parse(source, final = false).plainText)
        assertEquals(source, parser.parse(source, final = true).plainText)
        val next = assertIs<ReplyBlock.Heading>(parser.parse("# Next reply", final = false).blocks.single())
        assertEquals("Next reply", next.text.single().text)

        val fenced = parser.parse("```text\n$source\n```", final = true)
        assertEquals(source + "\n", assertIs<ReplyBlock.Code>(fenced.blocks.single()).source)
    }

    @Test
    fun codePreservesBlankLinesAndUsesUtf16HighlightOffsets() {
        val source = "```js\r\n/* comment\r\ncontinues */\r\n😀 const n = 42;\r\n\r\n```"
        val code = assertIs<ReplyBlock.Code>(ReplyMarkdownParser().parse(source, final = true).blocks.single())
        assertEquals("/* comment\r\ncontinues */\r\n😀 const n = 42;\r\n\r\n", code.source)
        assertEquals(listOf("/* comment", "continues */", "😀 const n = 42;", ""), code.lines.map { it.text })
        assertEquals(ReplyCodeKind.Comment, code.lines[1].highlights.single().kind)
        assertEquals(ReplyCodeHighlight(3, 8, ReplyCodeKind.Keyword), code.lines[2].highlights.first())

        val diff = assertIs<ReplyBlock.Code>(
            ReplyMarkdownParser().parse("```diff\n--- old\n+++ new\n-old\n+new\n```", final = true).blocks.single(),
        )
        assertTrue(diff.lines.take(2).all { it.highlights.isEmpty() })
        assertEquals(ReplyCodeKind.DiffDelete, diff.lines[2].highlights.single().kind)
        assertEquals(ReplyCodeKind.DiffAdd, diff.lines[3].highlights.single().kind)
    }
}
