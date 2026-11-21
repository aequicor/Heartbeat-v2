@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.InternalComposeUiApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyInline
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraReplyAnswer
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraRichDocument
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraStreamingPlainText
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraMessageStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.datatransfer.DataFlavor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuroraAnswerSelectionTest {
    @Test
    fun plainAnswerKeepsSelectionWhileALargeParagraphGrows() = runBlocking {
        val prefix = "Расширенный ответ "
        val paragraph = prefix.repeat(LargeParagraphLength / prefix.length + 1).take(LargeParagraphLength)
        verifySelection(paragraph, "$paragraph конец 🧪 שלום", target = listOf("Расширенный"))
    }

    @Test
    fun draggingAcrossSoftBoundaryCopiesTheExactSpaces() = runBlocking {
        val sentence = "Проверяем, как обычные пробелы сохраняются при переносе строки. "
        val source = sentence.repeat(155)
        val clipboard = RecordingClipboard()
        val clock = AuroraHoverTestClock()
        lateinit var selection: SelectionState
        lateinit var scroll: ScrollState
        val scene = ImageComposeScene(480, 460, coroutineContext = clock.context) {
            AuroraTestTheme {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    selection = rememberSelectionState()
                    scroll = rememberScrollState()
                    Column(Modifier.verticalScroll(scroll)) {
                        AuroraStreamingPlainText(source, selection, {})
                    }
                }
            }
        }
        try {
            clock.frame(scene, passes = 6)
            val nodes = selection.getSelectableTexts()
            assertEquals(2, nodes.size)
            val head = nodes.first().text
            val tail = nodes.last().text
            val headLayout = scene.positionedText(head).layout
            val start = headLayout.getLineStart(headLayout.lineCount - 1) + 2
            val finish = 12
            scroll.scrollTo((scene.positionedText(tail).cursor(finish).y - 230f).roundToInt())
            clock.frame(scene)
            val from = scene.positionedText(head).cursor(start)
            val to = scene.positionedText(tail).cursor(finish)
            assertTrue(from.y in 0f..460f && to.y in 0f..460f)
            scene.dragSelection(from, to)
            clock.frame(scene)
            assertEquals(listOf(head.substring(start), tail.take(finish)), selection.selectedTexts.map { it.text })
            scene.copySelection(clock)
            assertEquals(source.substring(start, head.length + finish), clipboard.text)
        } finally {
            scene.close()
        }
    }

    @Test
    fun richAnswerKeepsSelectionAcrossBlocksWhileContentGrows() = runBlocking {
        val target = listOf("Заголовок", "Подробности")
        val sections = List(500) { index ->
            "Раздел ${index + 1} объясняет, как сохранённое выделение ведёт себя при получении новых фрагментов."
        }
        val initial = listOf(
            ReplyBlock.Heading(0, 2, listOf(ReplyInline("Заголовок"))),
            ReplyBlock.Paragraph(10, listOf(listOf(ReplyInline("Подробности", bold = true)))),
        ) + sections.mapIndexed { index, section ->
            ReplyBlock.Paragraph(24 + index, listOf(listOf(ReplyInline(section))))
        }
        val appended = initial + ReplyBlock.Paragraph(24 + sections.size, listOf(listOf(ReplyInline("Продолжение"))))
        val source = (target + sections).joinToString("\n")
        verifySelection(source, "$source\nПродолжение", target, initial, appended)
    }

    @Test
    fun changingTheSelectionOnlyProtectsTheBlocksItReaches() = runBlocking {
        val heading = "Original heading"
        val tail = "Independent block"
        val headingBlock = ReplyBlock.Heading(0, 2, listOf(ReplyInline(heading)))
        fun replyDocument(growing: String? = null, following: String? = null) = ReplyDocument(buildList {
            add(headingBlock)
            growing?.let { add(ReplyBlock.Paragraph(20, listOf(listOf(ReplyInline(it))))) }
            following?.let { add(ReplyBlock.Paragraph(80, listOf(listOf(ReplyInline(it))))) }
        })
        val clock = AuroraHoverTestClock()
        var document by mutableStateOf(replyDocument())
        lateinit var selection: SelectionState
        val scene = ImageComposeScene(390, 600, coroutineContext = clock.context) {
            AuroraTestTheme {
                selection = rememberSelectionState()
                AuroraRichDocument(document, false, selection)
            }
        }
        suspend fun show(expected: String) = awaitSelectableText(scene, clock, { selection }, expected, "\n")
        try {
            show(heading)
            selection.select(TextRange(0, 4))
            clock.frame(scene)
            document = replyDocument(tail)
            show("$heading\n$tail")

            selection.select(TextRange(0, heading.length))
            clock.frame(scene)
            val growingTail = "$tail continues"
            document = replyDocument(growingTail)
            show("$heading\n$growingTail")
            assertEquals(listOf(heading), selection.selectedTexts.map { it.text })

            selection.select(TextRange(0, heading.length + tail.length))
            clock.frame(scene)
            val latestTail = "$growingTail here"
            val last = "Another independent block"
            document = replyDocument(latestTail, last)
            show("$heading\n$growingTail\n$last")
            assertEquals(listOf(heading, tail), selection.selectedTexts.map { it.text })

            selection.clear()
            show("$heading\n$latestTail\n$last")
        } finally {
            scene.close()
        }
    }

    private suspend fun verifySelection(
        initial: String,
        final: String,
        target: List<String>,
        blocks: List<ReplyBlock>? = null,
        appended: List<ReplyBlock>? = null,
    ) {
        val reply = Reply("reply", "14:00", status = ReplyStatus.Working)
        val live = MutableStateFlow(LiveText(reply.id, initial, blocks?.let { ReplyDocument(it) }))
        val clock = AuroraHoverTestClock()
        lateinit var selection: SelectionState
        val scene = ImageComposeScene(390, 600, coroutineContext = clock.context) {
            AuroraTestTheme {
                selection = rememberSelectionState()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AuroraReplyAnswer(reply, false, {}, live, selection, finalResize = false, onResizeEnd = {})
                }
            }
        }
        try {
            val delimiter = if (blocks == null) "" else "\n"
            awaitSelectableText(scene, clock, { selection }, initial, delimiter)
            selection.select(TextRange(0, target.sumOf { it.length }))
            clock.frame(scene)
            val selected = selection.selectedTexts
            assertEquals(target, selected.map { it.text })

            live.value = LiveText(reply.id, final, appended?.let { ReplyDocument(it) })
            clock.frame(scene)
            val whileSelected = if (blocks == null) initial else final
            awaitSelectableText(scene, clock, { selection }, whileSelected, delimiter)
            assertEquals(selected, selection.selectedTexts)

            selection.clear()
            awaitSelectableText(scene, clock, { selection }, final, delimiter)
        } finally {
            scene.close()
        }
    }

    private suspend fun awaitSelectableText(
        scene: ImageComposeScene,
        clock: AuroraHoverTestClock,
        selection: () -> SelectionState,
        expected: String,
        delimiter: String,
    ) = withTimeout(5_000) {
        do {
            clock.frame(scene)
            val shown = selection().getSelectableTexts().joinToString(delimiter) { it.text }
            if (shown != expected) delay(10)
        } while (shown != expected)
    }

    @Test
    fun clipboardPreservesSoftWrappingAndRealBreaksAcrossLayoutChanges() {
        val source = "Русский текст — с «кавычками», пробелами и длинным содержанием. ".repeat(85) + "\n\n" +
            "Следующий абзац сохраняется в буфере. ".repeat(140)
        val clipboard = RecordingClipboard()
        val clock = AuroraHoverTestClock()
        var width by mutableStateOf(390.dp)
        var chunkedHeight = 0
        var nativeHeight = 0
        lateinit var selection: SelectionState
        val scene = ImageComposeScene(880, 720, density = Density(1.5f), coroutineContext = clock.context) {
            AuroraTestTheme {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    selection = rememberSelectionState()
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AuroraStreamingPlainText(
                            source, selection, {}, Modifier.width(width).onSizeChanged { chunkedHeight = it.height },
                        )
                        BasicText(
                            source, Modifier.width(width).onSizeChanged { nativeHeight = it.height },
                            style = auroraMessageStyle(),
                        )
                    }
                }
            }
        }
        try {
            val initialBoundary = verifyChunkedCopy(scene, clock, selection = { selection }, clipboard, source)
            assertEquals(nativeHeight, chunkedHeight)
            val initialHeight = nativeHeight
            width = 460.dp
            verifyChunkedCopy(scene, clock, selection = { selection }, clipboard, source)
            assertEquals(nativeHeight, chunkedHeight)
            assertTrue(nativeHeight < initialHeight, "A wider layout must reflow the same text")
            width = 390.dp
            assertEquals(initialBoundary, verifyChunkedCopy(scene, clock, { selection }, clipboard, source))
            assertEquals(nativeHeight, chunkedHeight)
        } finally {
            scene.close()
        }
    }

    private fun verifyChunkedCopy(
        scene: ImageComposeScene, clock: AuroraHoverTestClock,
        selection: () -> SelectionState, clipboard: RecordingClipboard, source: String,
    ): Int {
        clock.frame(scene, passes = 6)
        val state = selection()
        val nodes = state.getSelectableTexts()
        assertTrue(nodes.size > 1, "The test must cross a measured line boundary")
        state.selectAll()
        clock.frame(scene)
        scene.copySelection(clock)
        assertEquals(source, clipboard.text)
        state.clear()
        return nodes.first().length
    }

    private fun ImageComposeScene.copySelection(clock: AuroraHoverTestClock) {
        val mac = System.getProperty("os.name").startsWith("Mac")
        for (event in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
            sendKeyEvent(KeyEvent(Key.C, event, isCtrlPressed = !mac, isMetaPressed = mac))
        }
        clock.frame(this)
    }

    private fun ImageComposeScene.dragSelection(from: Offset, to: Offset) {
        sendPointerEvent(PointerEventType.Press, from, button = PointerButton.Primary)
        for (step in 1..8) {
            sendPointerEvent(
                PointerEventType.Move, from + (to - from) * (step / 8f),
                buttons = PointerButtons(isPrimaryPressed = true),
            )
        }
        sendPointerEvent(PointerEventType.Release, to, button = PointerButton.Primary)
    }

    private fun ImageComposeScene.positionedText(text: String): PositionedText {
        val node = assertNotNull(semanticsOwners.first().rootSemanticsNode.findText(text))
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) == true)
        return PositionedText(node.positionInRoot, results.single())
    }

    private fun SemanticsNode.findText(text: String): SemanticsNode? =
        takeIf { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true }
            ?: children.firstNotNullOfOrNull { it.findText(text) }

    private class PositionedText(val origin: Offset, val layout: TextLayoutResult) {
        fun cursor(offset: Int): Offset = origin + layout.getCursorRect(offset).center
    }

    private class RecordingClipboard : Clipboard {
        var text: String? = null
            private set

        override suspend fun getClipEntry(): ClipEntry? = null

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            text = clipEntry?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String
        }
    }

    private companion object {
        const val LargeParagraphLength = 64 * 1024
    }
}
