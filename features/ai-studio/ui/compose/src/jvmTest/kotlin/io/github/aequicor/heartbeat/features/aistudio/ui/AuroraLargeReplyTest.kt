package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyInline
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistory
import java.awt.datatransfer.DataFlavor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CPU wall time of offscreen Compose frames. The test clock controls animation time, while
 * pointer events go through the same gesture handlers as the desktop window.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class AuroraLargeReplyTest {
    @Test
    fun aSinglePlainParagraphScrollsWhileItsTailGrows() {
        val source = plainReply()
        val stream = MutableStateFlow(LiveText(ReplyId, source))
        val fixture = HistoryFixture(Reply(ReplyId, ""), stream)
        fixture.use {
            val cold = fixture.measureAppearance(PlainTail)
            val scroll = fixture.measureScroll()
            val streaming = fixture.measureStream(::plainBatch) { index ->
                stream.value = stream.value.copy(text = stream.value.text + " " + plainBatch(index))
            }
            report("plain", source, cold, scroll, streaming)
        }
    }

    @Test
    fun largeRussianTextWithParagraphBreaksAndEmDashesScrollsAndStreams() = runBlocking {
        val tail = "Последний абзац большого ответа."
        val source = (1..240).joinToString("\n\n") { index ->
            "Проверка $index — сохраняем место чтения. " +
                "Пока агент готовит продолжение, пользователь может прокрутить диалог и выделить фрагмент. ".repeat(3)
        } + "\n\n" + tail
        val initial = readyLiveText(source)
        val stream = MutableStateFlow(initial)
        val fixture = HistoryFixture(Reply(ReplyId, ""), stream)
        fixture.use {
            val cold = fixture.measureAppearance(tail)
            val scroll = fixture.measureScroll()
            val streaming = fixture.measureStream(::russianBatch) { index ->
                val previous = stream.value
                val text = russianBatch(index)
                val paragraph = ReplyBlock.Paragraph(previous.text.length + 2, listOf(listOf(ReplyInline(text))))
                stream.value = LiveText(
                    ReplyId, previous.text + "\n\n" + text,
                    previous.document?.let { it.copy(blocks = it.blocks + paragraph) },
                )
            }
            report("plain-russian", source, cold, scroll, streaming)
        }
    }

    @Test
    fun mixedBlocksScrollWhileTheirTailGrows() = runBlocking {
        val (source, document) = mixedReply()
        val stream = MutableStateFlow(LiveText(ReplyId, source, document))
        val fixture = HistoryFixture(Reply(ReplyId, ""), stream)
        fixture.use {
            val cold = fixture.measureAppearance(MixedTail)
            val codeMarkers = mixedCodeMarkers()
            val fullCold = fixture.measureCodeReadiness(cold, codeMarkers)
            val scroll = fixture.measureScroll()
            val streaming = fixture.measureStream(::mixedBatch) { index -> stream.append(source, document, index) }
            report("mixed", source, cold, scroll, streaming)
            println(
                "AuroraLargeReply mixed-all-code coldReadyMs=${fullCold.ready / 1_000_000.0} " +
                    "coldRenderMaxMs=${fullCold.maxRender / 1_000_000.0} codeBlocks=${codeMarkers.size}",
            )
            fixture.verifyWheelDuringUpdate { stream.append(source, document, Frames) }
        }
    }

    @Test
    fun wheelOpensAnUnpreparedCompletedReportInTheMiddleOfHistory() = runBlocking {
        val (source) = mixedReply()
        val archived = Reply("archived-report", "10:20", text = source, status = ReplyStatus.Completed)
        val neighbors = List(6) { index ->
            val text = List(20) { line -> "Message $index, line $line" }.joinToString("\n")
            User("neighbor-$index", "10:00", text)
        }
        HistoryFixture(Reply(ReplyId, ""), motion = HbMotionPreference.Full).use { fixture ->
            fixture.state = AiStudioScreenState(messages = neighbors.take(3) + archived + neighbors.drop(3))
            fixture.frame()
            assertNull(archived.historyContent?.available)
            fixture.wheelToMessage(archived.id)
            fixture.awaitText(MixedTail)
            mixedCodeMarkers().forEach { assertNotNull(fixture.text(it)) }
        }
    }

    @Test
    fun fullyAnimatedHistoryShowsTheArchivedTailOnTheFirstPlacedFrameAndKeepsItsAnchor() = runBlocking {
        val (source) = mixedReply()
        val archived = Reply("full-effects", "10:20", text = source, status = ReplyStatus.Completed)
        HistoryFixture(Reply(ReplyId, ""), motion = HbMotionPreference.Full).use { fixture ->
            fixture.state = AiStudioScreenState(messages = listOf(archived, User("prompt", "", "Earlier prompt")))
            assertNull(assertNotNull(archived.historyContent).available)
            val shown = fixture.awaitAppearance(MixedTail, mixedCodeMarkers()).node
            assertTrue(shown.boundsInRoot.height > 0f, "The first placed report must already show its tail")
            val anchor = fixture.viewport(MixedTail)
            assertEquals(0, anchor.firstIndex)
            assertEquals(0, anchor.firstOffset)
            repeat(10) {
                fixture.advanceOpeningFrame()
                val current = fixture.viewport(MixedTail)
                assertEquals(anchor.cursorY, current.cursorY, 1f)
                assertEquals(anchor.firstOffset, current.firstOffset)
            }
        }
    }

    @Test
    fun wrappingCodeAfterArchivedRevealStillAnimatesTheReplyHeight() = runBlocking {
        val tail = "End of archived source."
        val line = "val value = " + "word".repeat(180)
        val archived = Reply(
            "archive-wrap", "10:20", text = "~~~kotlin\n$line\n~~~\n\n$tail", status = ReplyStatus.Completed,
        )
        HistoryFixture(Reply(ReplyId, ""), motion = HbMotionPreference.Full).use { fixture ->
            fixture.state = AiStudioScreenState(messages = listOf(archived))
            fixture.awaitText(tail)
            fixture.settle()
            val initialHeight = assertNotNull(fixture.viewport(tail).itemSize)
            fixture.clickAction("Перенос строк")
            fixture.advanceOpeningFrame()
            val earlyResizeHeight = assertNotNull(fixture.viewport(tail).itemSize)
            repeat(10) { fixture.advanceOpeningFrame() }
            val wrappedHeight = assertNotNull(fixture.viewport(tail).itemSize)
            assertTrue(wrappedHeight > initialHeight, "Wrapped reply height must grow: $initialHeight → $wrappedHeight")
            assertTrue(earlyResizeHeight < wrappedHeight, "Later code wrapping must retain its ordinary size tween")
        }
    }

    @Test
    fun visibleTailKeepsItsPositionAndSelectionWhileAllCodeBlocksOpen() = runBlocking {
        val (source, document) = mixedReply()
        val stream = MutableStateFlow(LiveText(ReplyId, source, document))
        HistoryFixture(Reply(ReplyId, ""), stream).use { fixture ->
            fixture.awaitText(MixedTail)
            val position = fixture.visibleCursor(MixedTail)
            val before = fixture.viewport(MixedTail)
            val expected = "Mixed-tail"
            val end = fixture.visibleCursor(MixedTail, expected.length)
            fixture.scene.sendPointerEvent(PointerEventType.Move, position)
            fixture.scene.sendPointerEvent(PointerEventType.Press, position, button = PointerButton.Primary)
            fixture.dragTo(end)

            fixture.awaitAppearance(MixedTail, mixedCodeMarkers()) {
                val current = fixture.viewport(MixedTail)
                assertEquals(
                    before.cursorY, current.cursorY, 1f,
                    "Opening moved the visible line from $before to $current",
                )
                assertEquals(position.x, fixture.visibleCursor(MixedTail).x, 1f)
            }
            fixture.scene.sendPointerEvent(PointerEventType.Release, end, button = PointerButton.Primary)
            fixture.copySelection()
            assertEquals(expected, fixture.clipboard.text())
        }
    }

    @Test
    fun replacingAReplyWhileItsDocumentIsStagingShowsAndCopiesOnlyTheReplacement() = runBlocking {
        val (source, document) = mixedReply()
        val stream = MutableStateFlow(LiveText(ReplyId, source, document))
        val oldCode = mixedCodeMarkers().first()
        val replacement = "Replacement report is ready."
        val replacementId = "replacement"
        val revisedCode = "val result = verifyStatus()"
        val next = readyLiveText("```kotlin\n$revisedCode\n```\n\n$replacement").copy(replyId = replacementId)
        HistoryFixture(Reply(ReplyId, ""), stream).use { fixture ->
            fixture.awaitRichStaging()
            assertNull(fixture.text(MixedTail))
            assertNull(fixture.clipboard.text())

            fixture.state = fixture.state.copy(live = Reply(replacementId, ""))
            stream.value = next
            fixture.awaitAppearance(replacement, listOf(revisedCode)) {
                assertNull(fixture.text(MixedTail))
                assertNull(fixture.text(oldCode))
            }

            val start = fixture.visibleCursor(replacement)
            val end = fixture.visibleCursor(replacement, replacement.length)
            fixture.scene.sendPointerEvent(PointerEventType.Move, start)
            fixture.scene.sendPointerEvent(PointerEventType.Press, start, button = PointerButton.Primary)
            fixture.dragTo(end)
            fixture.scene.sendPointerEvent(PointerEventType.Release, end, button = PointerButton.Primary)
            fixture.copySelection()
            assertEquals(replacement, fixture.clipboard.text())
        }
    }

    @Test
    fun longReasoningExpandsFromItsHeaderAndScrollsWhileReceivingMoreText() {
        val detail = (1..1_000).joinToString("\n") { "Reasoning step $it checked the pending action and its result." }
        val thinking = WorkEvent("thinking", ThinkingTitle, "Checking assumptions…", detail,
            WorkEventKind.Thinking, StepStatus.Current)
        val reply = Reply(
            ReplyId, "", steps = listOf(Step("scan", "Inspect the result", "", StepStatus.Current, listOf(thinking))),
        )
        val fixture = HistoryFixture(reply)
        fixture.use {
            fixture.frame()
            val disclosure = assertNotNull(fixture.text(ThinkingTitle)).boundsInRoot.center
            val cold = fixture.measureAppearance("Reasoning step 1 checked") { fixture.click(disclosure) }
            assertContains(fixture.state.expandedEvents, "$ReplyId/scan/thinking")
            val scroll = fixture.measureScroll()
            var details = detail
            val streaming = fixture.measureStream(::thinkingBatch) { index ->
                details += "\n" + thinkingBatch(index)
                val next = thinking.copy(details = details)
                val step = reply.steps.single().copy(events = listOf(next))
                fixture.state = fixture.state.copy(live = reply.copy(steps = listOf(step)))
            }
            report("thinking-disclosure", detail, cold, scroll, streaming)
        }
    }

    @Test
    fun appendingToTheSameGiantMessageKeepsTheReadingLineInTheViewport() {
        val source = plainReply()
        val stream = MutableStateFlow(LiveText(ReplyId, source))
        val fixture = HistoryFixture(Reply(ReplyId, ""), stream)
        fixture.use {
            fixture.awaitText(PlainTail)
            fixture.wheelAwayFromTail(0.75f)
            fixture.awaitScrollRest()
            val before = fixture.viewport(ReadingAnchor)
            val appendMarker = "New revision reached the answer."
            stream.value = stream.value.copy(text = "$source " + PlainSentence.repeat(4) + appendMarker)
            fixture.awaitText(appendMarker)
            fixture.settle()
            val after = fixture.viewport(ReadingAnchor)
            println("AuroraLargeReply reading-anchor before=$before after=$after")
            assertEquals(before.cursorY, after.cursorY, 1f, "Appending must keep the same line visible")
        }
    }

    @Test
    fun appendingDuringAnActiveWheelKeepsTheSameReadingLineAsTheWheelAlone() {
        val source = plainReply()
        val appendMarker = "The wheel-time revision reached the answer."
        val control = wheelReadingViewport(source, null, appendMarker)
        val withAppend = wheelReadingViewport(source, "$source " + PlainSentence.repeat(4) + appendMarker, appendMarker)
        println("AuroraLargeReply wheel-anchor control=$control append=$withAppend")
        assertEquals(control.before.cursorY, withAppend.before.cursorY, 1f, "Both gestures start from the same line")
        assertEquals(
            control.after.cursorY, withAppend.after.cursorY, 1f,
            "An append must preserve the wheel's reading position",
        )
    }

    private fun wheelReadingViewport(source: String, revision: String?, appendMarker: String): WheelResult {
        val stream = MutableStateFlow(LiveText(ReplyId, source))
        val fixture = HistoryFixture(Reply(ReplyId, ""), stream)
        fixture.use {
            fixture.awaitText(PlainTail)
            fixture.startWheel(0.75f)
            val before = fixture.viewport(ReadingAnchor)
            assertTrue(before.cursorY in 100f..540f, "The anchor needs room on both sides of the viewport")
            if (revision != null) stream.value = stream.value.copy(text = revision)
            fixture.deepSettle()
            if (revision != null) assertNotNull(fixture.text(appendMarker), "The new revision must reach Compose")
            return WheelResult(before, fixture.viewport(ReadingAnchor))
        }
    }

    @Test
    fun dragSelectionCanBeCopiedWhileTheSameReplyReceivesNewText() = runBlocking {
        exerciseRichSelection(withCode = false)
    }

    @Test
    fun dragSelectionSurvivesStreamingInAReplyWithTenCodeBlocks() = runBlocking {
        exerciseRichSelection(withCode = true)
    }

    private suspend fun exerciseRichSelection(withCode: Boolean) {
        val (source, document) = mixedReply(withCode)
        val stableText = "alpha bronze calculus delta stays available for selection"
        val belowSelection = List(8) { "Context stays visible below the source selection." }
        val anchor = ReplyBlock.Paragraph(source.length + 2, listOf(listOf(ReplyInline(stableText))))
        val below = ReplyBlock.Paragraph(
            anchor.id + stableText.length + 2, belowSelection.map { listOf(ReplyInline(it)) },
        )
        val selectable = document.copy(blocks = document.blocks + anchor + below)
        val withAnchor = "$source\n\n$stableText\n\n" + belowSelection.joinToString("\n")
        val stream = MutableStateFlow(LiveText(ReplyId, withAnchor, selectable))
        val fixture = HistoryFixture(Reply(ReplyId, ""), stream)
        fixture.use {
            fixture.awaitText(stableText)
            val initial = fixture.viewport(stableText)
            val expected = "alpha bronze calculus"
            val from = fixture.visibleCursor(stableText)
            val through = fixture.visibleCursor(stableText, 7)
            fixture.scene.sendPointerEvent(PointerEventType.Move, from)
            fixture.scene.sendPointerEvent(PointerEventType.Press, from, button = PointerButton.Primary)
            fixture.dragTo(through)
            fixture.frame()
            val duringDrag = fixture.viewport(stableText)
            stream.append(withAnchor, selectable, 0)
            val frames = fixture.awaitTextFrames(mixedBatch(0))
            val afterAppend = fixture.viewport(stableText)
            println("AuroraLargeReply drag-anchor initial=$initial drag=$duringDrag append=$afterAppend frames=$frames")
            assertEquals(
                duringDrag.cursorY, afterAppend.cursorY, 1f,
                "A new block must keep the selected glyph in place",
            )
            val to = fixture.visibleCursor(stableText, expected.length)
            fixture.dragTo(to)
            fixture.scene.sendPointerEvent(PointerEventType.Release, to, button = PointerButton.Primary)
            fixture.frame()
            stream.append(withAnchor, selectable, 1)
            fixture.awaitText(mixedBatch(1))
            fixture.copySelection()
            assertEquals(expected, fixture.clipboard.text())
        }
    }

    private suspend fun mixedReply(withCode: Boolean = true): Pair<String, ReplyDocument> {
        val source = buildString {
            repeat(MixedSections) { index ->
                appendLine("## Check ${index + 1}")
                appendLine()
                appendLine(MixedParagraph)
                appendLine()
                if (index % MixedCodeInterval == 0) {
                    if (withCode) {
                        appendLine("```kotlin")
                        repeat(18) { appendLine("val result$it = checkStatus($index)") }
                        appendLine("```")
                        appendLine()
                    }
                    appendLine("| Item | State |")
                    appendLine("| --- | :---: |")
                    appendLine("| Result | Ready |")
                    appendLine()
                }
            }
            appendLine(MixedTail)
        }
        val history = assertNotNull(Reply(ReplyId, "", text = source, status = ReplyStatus.Completed).historyContent)
        return source to history.document()
    }

    private suspend fun readyLiveText(source: String): LiveText {
        val content = assertNotNull(Reply(ReplyId, "", text = source, status = ReplyStatus.Completed).historyContent)
        return LiveText(ReplyId, source, if (content.plain) null else content.document())
    }

    private fun MutableStateFlow<LiveText>.append(source: String, document: ReplyDocument, index: Int) {
        val tail = (0..index).joinToString(" ", transform = ::mixedBatch)
        val block = ReplyBlock.Paragraph(source.length + 2, listOf(listOf(ReplyInline(tail))))
        value = LiveText(ReplyId, "$source\n\n$tail", document.copy(blocks = document.blocks + block))
    }

    private class HistoryFixture(
        reply: Reply,
        stream: MutableStateFlow<LiveText> = MutableStateFlow(LiveText(ReplyId, "")),
        motion: HbMotionPreference = HbMotionPreference.Reduced,
    ) : AutoCloseable {
        private val clock = AuroraHoverTestClock()
        private val list = LazyListState()
        private val older = List(12) { User("earlier-$it", "", "Earlier reply $it") }
        val clipboard = MemoryClipboard()
        var state by mutableStateOf(AiStudioScreenState(live = reply, messages = older))
        val scene = ImageComposeScene(980, ViewportHeight, coroutineContext = clock.context) {
            AuroraTestTheme(motion = motion) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    AuroraHistory(
                        state, stream, list, false, ::dispatch, PaddingValues(36.dp), Modifier.fillMaxSize(),
                        viewportHeight = ViewportHeight.dp,
                    )
                }
            }
        }

        private fun dispatch(action: AiStudioAction) {
            if (action is AiStudioAction.ToggleWorkEvent) {
                val key = "${action.replyId}/${action.stepId}/${action.eventId}"
                val expanded = state.expandedEvents
                state = state.copy(expandedEvents = if (key in expanded) expanded - key else expanded + key)
            }
        }

        fun frame() = clock.frame(scene, passes = 1)
        fun advanceOpeningFrame() { renderFrame() }

        fun clickAction(label: String) {
            val action = scene.semanticsOwners.firstNotNullOfOrNull { owner ->
                owner.rootSemanticsNode.findPlaced {
                    label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                }
            }
            assertTrue(assertNotNull(action).config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
        }
        fun text(label: String): SemanticsNode? =
            scene.semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.text(label) }

        fun settle() = clock.advance(scene, 180, passes = 2)

        fun startWheel(distance: Float) {
            wheel(-distance)
            clock.advance(scene, 180, passes = 2)
            assertTrue(list.isScrollInProgress, "An append must occur while the wheel is active")
        }

        fun deepSettle() {
            // Use the same virtual and wall-clock opportunities in each branch. A renderer
            // may prepare the revision on Dispatchers.Default while the wheel is moving.
            repeat(Frames) {
                clock.advance(scene, 16, passes = 1)
                Thread.sleep(1)
            }
            assertTrue(!list.isScrollInProgress, "The wheel gesture must settle before comparing coordinates")
        }

        fun awaitScrollRest() {
            val deadline = System.nanoTime() + ReadyTimeoutNanos
            // Advance the scene clock and let dispatcher work finish between frames.
            do {
                clock.advance(scene, 16, passes = 1)
                if (!list.isScrollInProgress) return
                Thread.sleep(1)
            } while (System.nanoTime() < deadline)
            error("Wheel animation did not settle at viewport position ${position()}")
        }

        fun viewport(marker: String): ReadingViewport {
            val firstIndex = list.firstVisibleItemIndex
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstIndex }
            val (cursor, layoutTop) = sourceCursor(marker, 0)
            return ReadingViewport(
                firstIndex, list.firstVisibleItemScrollOffset, item?.offset, item?.size,
                cursor.y, layoutTop, list.isScrollInProgress,
            )
        }

        fun visibleCursor(marker: String, character: Int = 0): Offset {
            val (position) = sourceCursor(marker, character)
            assertTrue(
                position.y in 0f..ViewportHeight.toFloat(),
                "The source cursor is outside the viewport: $position",
            )
            return position
        }

        private fun sourceCursor(marker: String, character: Int): Pair<Offset, Float> {
            val node = assertNotNull(text(marker))
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) == true)
            val layout = layouts.first { marker in it.layoutInput.text.text }
            val offset = layout.layoutInput.text.text.indexOf(marker) + character
            val rect = layout.getCursorRect(offset)
            return node.positionInRoot + rect.center + Offset(0.5f, 0f) to node.positionInRoot.y
        }

        fun awaitText(label: String): SemanticsNode = awaitAppearance(label).node
        fun awaitTextFrames(label: String): Int = awaitAppearance(label).frames

        fun awaitRichStaging() {
            val find: (String) -> SemanticsNode? = { label ->
                scene.semanticsOwners.firstNotNullOfOrNull {
                    it.rootSemanticsNode.findPlaced { node ->
                        label in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    }
                }
            }
            val message = "Подготавливаю сообщение…"
            awaitAppearance(message, find = find)
            renderFrame()
            assertNotNull(find(message), "The original reply must still be staging before it is replaced")
        }

        fun measureAppearance(
            label: String,
            readyMarkers: List<String> = listOf(),
            publish: () -> Unit = {},
        ): Appearance {
            val start = System.nanoTime()
            publish()
            val appearance = awaitAppearance(label, readyMarkers)
            return Appearance(System.nanoTime() - start, appearance.visibleRender, appearance.maxRender)
        }

        fun measureCodeReadiness(initial: Appearance, markers: List<String>): Appearance {
            val remaining = markers.filter { text(it) == null }
            if (remaining.isEmpty()) return initial
            val following = measureAppearance(MixedTail, remaining)
            return following.copy(
                ready = initial.ready + following.ready,
                maxRender = maxOf(initial.maxRender, following.maxRender),
            )
        }

        // Rich spans are prepared on Dispatchers.Default. Measuring a placeholder would
        // record an incomplete first frame or skip published updates while the worker catches up.
        fun awaitAppearance(
            label: String,
            readyMarkers: List<String> = listOf(),
            find: (String) -> SemanticsNode? = ::text,
            onFrame: () -> Unit = {},
        ): VisibleText {
            val deadline = System.nanoTime() + ReadyTimeoutNanos
            var maxRender = 0L
            var frames = 0
            do {
                val render = renderFrame()
                onFrame()
                frames++
                maxRender = maxOf(maxRender, render)
                val node = find(label)
                if (node != null && readyMarkers.all { text(it) != null }) {
                    return VisibleText(node, render, maxRender, frames)
                }
                Thread.sleep(1)
            } while (System.nanoTime() < deadline)
            error("The scene must show the text: $label")
        }

        private fun renderFrame(): Long {
            clock.context.scheduler.advanceTimeBy(16)
            clock.context.scheduler.runCurrent()
            return measureNanoTime { scene.render(clock.nanoTime).close() }
        }

        fun click(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
        }

        fun dragTo(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Move, at, buttons = PointerButtons(isPrimaryPressed = true))
        }

        fun copySelection() {
            val mac = System.getProperty("os.name").startsWith("Mac")
            for (event in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
                scene.sendKeyEvent(KeyEvent(Key.C, event, isCtrlPressed = !mac, isMetaPressed = mac))
            }
            frame()
        }

        private fun wheel(y: Float) =
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(430f, 310f), scrollDelta = Offset(0f, y))

        private fun position() = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset

        fun wheelAwayFromTail(distance: Float = 4f): Float {
            val original = position()
            wheel(-distance)
            settle()
            val towardOlder = if (position() != original) -1f else 1f
            if (towardOlder > 0) {
                wheel(distance)
                settle()
            }
            assertTrue(position() != original, "Wheel must move the history viewport")
            return towardOlder
        }

        fun wheelToMessage(id: String) {
            val towardOlder = wheelAwayFromTail(4f)
            repeat(30) {
                if (list.layoutInfo.visibleItemsInfo.any { item -> item.key == id }) return
                wheel(towardOlder * 4f)
                awaitScrollRest()
            }
            error("The wheel did not open message $id from ${position()}")
        }

        fun measureScroll(): LongArray {
            val towardOlder = wheelAwayFromTail()
            val historyPosition = position()
            var moved = false
            val samples = LongArray(Frames) { index ->
                if (position() != historyPosition) moved = true
                wheel(if (index < Frames / 2) towardOlder else -towardOlder)
                renderFrame()
            }
            assertTrue(moved, "Wheel must continue to move the history viewport")
            return samples
        }

        fun measureStream(marker: (Int) -> String, publish: (Int) -> Unit): List<Appearance> {
            list.requestScrollToItem(0)
            frame()
            return List(Frames) { index -> measureAppearance(marker(index)) { publish(index) } }
        }

        fun verifyWheelDuringUpdate(publish: () -> Unit) {
            val before = position()
            wheel(-4f)
            publish()
            clock.advance(scene, 180, passes = 2)
            if (position() == before) {
                wheel(4f)
                clock.advance(scene, 180, passes = 2)
            }
            assertTrue(position() != before, "Wheel must move after a live revision")
        }

        override fun close() = scene.close()
    }

    private data class VisibleText(
        val node: SemanticsNode, val visibleRender: Long, val maxRender: Long, val frames: Int,
    )
    private data class Appearance(val ready: Long, val visibleRender: Long, val maxRender: Long)
    private data class ReadingViewport(
        val firstIndex: Int, val firstOffset: Int, val itemOffset: Int?, val itemSize: Int?,
        val cursorY: Float, val layoutTop: Float, val scrolling: Boolean,
    )
    private data class WheelResult(val before: ReadingViewport, val after: ReadingViewport)

    private class MemoryClipboard : Clipboard {
        private var entry: ClipEntry? = null
        override suspend fun getClipEntry() = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
        }

        fun text(): String? = entry?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String
    }

    private companion object {
        const val ReplyId = "stress"
        const val ThinkingTitle = "Tracing the pending action"
        const val PlainSentence = "The assistant retained the reading position while the next result arrived. "
        const val PlainTail = "Plain-tail-marker"
        const val MixedTail = "Mixed-tail-marker"
        const val MixedSections = 150
        const val MixedCodeInterval = 15
        const val MixedParagraph =
            "A **formatted** paragraph shows the result of the current check and describes the next step."
        const val ReadingAnchor = "Stable-reading-line"
        const val ViewportHeight = 640
        const val Frames = 36
        const val ReadyTimeoutNanos = 5_000_000_000L

        fun plainReply() = PlainSentence.repeat(1_594) + "$ReadingAnchor " + PlainSentence.repeat(6) + PlainTail
        fun plainBatch(index: Int) = "Result ${index + 1} was checked."
        fun russianBatch(index: Int) = "Поступило обновление ${index + 1} — место чтения сохранено."
        fun mixedBatch(index: Int) = "Received streamed batch ${index + 1}."
        fun mixedCodeMarkers() = (0 until MixedSections step MixedCodeInterval).map { "val result0 = checkStatus($it)" }
        fun thinkingBatch(index: Int) = "New reasoning detail ${index + 1}."

        fun SemanticsNode.text(label: String): SemanticsNode? =
            findPlaced { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { label in it.text } }

        fun SemanticsNode.findPlaced(matches: (SemanticsNode) -> Boolean): SemanticsNode? = when {
            !layoutInfo.isPlaced -> null
            matches(this) -> this
            config.isClearingSemantics -> null
            else -> children.firstNotNullOfOrNull { it.findPlaced(matches) }
        }

        fun report(name: String, source: String, cold: Appearance, scroll: LongArray, streaming: List<Appearance>) {
            fun LongArray.percentile(p: Double) = sorted()[(ceil(size * p).toInt() - 1)] / 1_000_000.0
            fun List<Appearance>.percentileOf(selector: (Appearance) -> Long) =
                map(selector).toLongArray().percentile(0.95)
            println(
                "AuroraLargeReply $name chars=${source.length} coldReadyMs=${cold.ready / 1_000_000.0} " +
                    "coldRenderMaxMs=${cold.maxRender / 1_000_000.0} scrollRenderP95Ms=${scroll.percentile(0.95)} " +
                    "visibleRevisions=${streaming.size}/$Frames " +
                    "publishToVisibleP95Ms=${streaming.percentileOf(Appearance::ready)} " +
                    "visibleRenderP95Ms=${streaming.percentileOf(Appearance::visibleRender)} " +
                    "streamRenderMaxMs=${streaming.maxOf(Appearance::maxRender) / 1_000_000.0}",
            )
        }
    }
}
