package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraMock
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.LargeMessageDemo
import io.github.aequicor.heartbeat.features.aistudio.presentation.rich.ReplyMarkdownParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownDemoTest {
    @Test
    fun reasoningInterimAndFinalSamplesContainDifferentRenderableBlocks() {
        val sources = listOf(LargeMessageDemo.reasoning.joinToString(""),
            AuroraMock.interimSample, LargeMessageDemo.answer("рассуждение"), AuroraMock.richSample)
        for (source in sources) {
            val blocks = ReplyMarkdownParser(literalFastPath = false).parse(source, final = true).blocks
            assertTrue(blocks.any { it is ReplyBlock.Heading })
            assertTrue(blocks.any { it is ReplyBlock.Paragraph })
            assertTrue(blocks.any { it is ReplyBlock.ListBlock })
            assertTrue(blocks.any { it is ReplyBlock.Quote })
            assertTrue(blocks.any { it is ReplyBlock.Table })
        }
    }

    @Test
    fun savedReasoningDeclaresMarkdownAndStepResultsOwnTheirParsedRevision() = runTest {
        val reply = LargeMessageDemo.history().filterIsInstance<Reply>().first()
        val event = reply.steps.single().events.single()
        assertEquals(DetailFormat.Markdown, event.detailsFormat)
        val reasoning = assertNotNull(event.markdownDetails).document()
        assertTrue(reasoning.blocks.any { it is ReplyBlock.Code })
        val interim = reply.copy(interim = AuroraMock.interimSample)
        val first = assertNotNull(interim.interimContent).document()
        val step = reply.steps.single().copy(result = AuroraMock.interimSample)
        assertEquals(first.blocks, assertNotNull(step.resultContent).document().blocks)
        val changed = step.copy(result = "Результат обновлён")
        assertEquals("Результат обновлён", assertNotNull(changed.resultContent).document().plainText)
    }

    @Test
    fun plainTextExampleRemainsAvailable() {
        val plain = LargeMessageDemo.answer("текст")
        assertEquals(plain, ReplyMarkdownParser().parse(plain, final = true).plainText)
    }
}
