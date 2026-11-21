@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File as ResultFile
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistory
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistoryReflow
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraTailFollow
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraTailControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class AuroraHistoryReflowTest : AuroraWorkSceneTest() {
    @Test
    fun aCorrectionCapturedBeforeManualScrollCannotReturnTheReaderToTheBottom() = runTest {
        val list = LazyListState()
        val tail = AuroraTailFollow(list)
        val reply = Reply("stream", "")
        val stream = MutableStateFlow(LiveText(reply.id, List(32) { "Строка $it" }.joinToString("\n")))
        val scene = ImageComposeScene(900, 540, coroutineContext = clock.context) {
            AuroraTestTheme {
                AuroraHistory(AiStudioScreenState(live = reply), stream, list, false, {}, PaddingValues(48.dp),
                    modifier = Modifier.fillMaxSize(), tailFollow = tail)
            }
        }
        try {
            clock.frame(scene)
            val before = tail.beforeResize()
            assertTrue(before.following)
            tail.resized(reply.id, 120, before, backgroundScope, selected = false)
            tail.userScroll()
            list.requestScrollToItem(0, 64)
            clock.frame(scene)
            val offset = list.firstVisibleItemScrollOffset
            tail.resized(reply.id, 120, before, backgroundScope, selected = false)
            testScheduler.runCurrent()
            clock.frame(scene)
            assertEquals(offset, list.firstVisibleItemScrollOffset)
            assertFalse(tail.following)
        } finally { scene.close() }
    }

    @Test
    fun readingSurvivesGrowthAndCompletionAndTheExistingButtonResumesFollowing() {
        val list = LazyListState()
        val tail = AuroraTailFollow(list)
        val reply = Reply("stream", "")
        val older = User("older", "", "Якорь чтения истории")
        val history = listOf(older) + List(14) { User("old-$it", "", "Предыдущая реплика $it") }
        val stream = MutableStateFlow(LiveText(reply.id, "Первая строка\nВторая строка\nТретья строка"))
        var state by mutableStateOf(AiStudioScreenState(live = reply, messages = history))
        val scene = ImageComposeScene(900, 540, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.fillMaxSize()) {
                    AuroraHistory(state, stream, list, false, {}, PaddingValues(48.dp),
                        modifier = Modifier.fillMaxSize(), tailFollow = tail)
                    AuroraTailControl(list, false, Modifier.align(Alignment.TopEnd), onJump = tail::returnToTail)
                }
            }
        }
        try {
            clock.frame(scene)
            list.requestScrollToItem(0, 32)
            clock.frame(scene)
            assertFalse(tail.following)
            val reading = scene.textTop(older.text)
            val more = List(6) { "\nНовая строка $it" }.joinToString("")
            stream.value = stream.value.copy(text = stream.value.text + more)
            clock.frame(scene)
            assertEquals(reading, scene.textTop(older.text), 1f)
            val button = assertNotNull(scene.semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.tailButton() })
            assertTrue(button.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
            clock.frame(scene)
            assertTrue(tail.following)
            assertFalse(list.canScrollBackward)
            stream.value = stream.value.copy(text = stream.value.text + "\nПродолжение у нижнего края")
            clock.frame(scene)
            assertFalse(list.canScrollBackward)
            list.requestScrollToItem(0, 32)
            clock.frame(scene)
            val beforeFinal = scene.textTop(older.text)
            val final = reply.copy(text = stream.value.text + "\nФинальный остаток", status = ReplyStatus.Completed)
            state = state.copy(live = null, messages = listOf(final) + history)
            stream.value = LiveText()
            clock.frame(scene)
            assertEquals(beforeFinal, scene.textTop(older.text), 1f)
            clock.advance(scene, 400)
            assertEquals(beforeFinal, scene.textTop(older.text), 1f)
            assertFalse(tail.following)
            list.requestScrollToItem(0)
            clock.frame(scene)
            assertTrue(tail.following)
        } finally { scene.close() }
    }

    @Test
    fun collapseAtTailMovesNewerMessagesUpAndTheSameHeaderReopensImmediately() {
        val fixture = pencilTimelineFixture()
        val reply = fixture.copy(
            status = ReplyStatus.Completed,
            steps = fixture.steps.take(1),
            text = "Проверка контракта завершена.",
        )
        val newer = User("recent", "14:21", "Новая реплика после ответа")
        val items = listOf(newer, reply) + List(12) { index -> User("old-$index", "14:00", "Реплика $index") }
        val live = MutableStateFlow(AiStudioScreenState.LiveText())
        var expanded by mutableStateOf(setOf(reply.id))
        val scene = ImageComposeScene(900, 540, coroutineContext = clock.context) {
            AuroraTestTheme {
                AuroraHistory(
                    AiStudioScreenState(messages = items, expandedWork = expanded), live,
                    rememberLazyListState(), touch = false,
                    onAction = { if (it == AiStudioAction.ToggleWork(reply.id)) {
                        expanded = if (reply.id in expanded) setOf() else setOf(reply.id)
                    } },
                    padding = PaddingValues(48.dp), modifier = Modifier.fillMaxSize().background(HbAuroraColors.Canvas),
                )
            }
        }
        try {
            clock.frame(scene)
            val header = assertNotNull(scene.textNode("Ход работы · 1 этап")).boundsInRoot.center
            val original = assertNotNull(scene.textNode(newer.text)).boundsInRoot.top
            val oldText = "Реплика 0"
            val olderTop = scene.textTop(oldText)
            scene.moveTo(header)
            scene.click(header)
            assertTrue(expanded.isEmpty())
            assertEquals(original, scene.textTop(newer.text), 1f)
            scene.click(header)
            assertTrue(reply.id in expanded, "The collapsing header can be opened before the transition finishes")
            scene.click(header)
            assertTrue(expanded.isEmpty())
            clock.advance(scene, 100)
            val intermediate = assertNotNull(scene.textNode(newer.text)).boundsInRoot.top
            scene.capture("aurora-work-collapse-during-motion")
            clock.advance(scene, 140)
            val settled = assertNotNull(scene.textNode(newer.text)).boundsInRoot.top
            assertTrue(settled < intermediate && intermediate < original)
            assertTrue(olderTop < scene.textTop(oldText))
            scene.click(header)
            assertTrue(reply.id in expanded, "The header can be clicked again without another hover delay")
            clock.advance(scene, 240)
            clock.advance(scene, 240)
            assertEquals(original, scene.textTop(newer.text), 1f)
            assertEquals(olderTop, scene.textTop(oldText), 1f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun openingHistoryMakesRoomAboveAndBelowTheFixedHeader() {
        verifyOpeningDirection(0)
        verifyOpeningDirection(1)
        verifyOpeningDirection(0, olderCount = 1)
    }

    private fun replyWithCompletedStep() = pencilTimelineFixture().run {
        copy(
            status = ReplyStatus.Completed, steps = steps.take(1),
            text = "Проверка контракта завершена. Состояние очереди сохранено.",
        )
    }

    private fun verifyOpeningDirection(startIndex: Int, olderCount: Int = 20) {
        val reply = replyWithCompletedStep()
        val newer = List(4) { User("new-$it", "", "Следующая реплика $it") }
        val earlierText = "Длинная верхняя реплика с проверкой перекрытия. Текст должен быть виден целиком."
        val older = List(olderCount) { User("old-$it", "", if (it == 0) earlierText else "Предыдущая реплика $it") }
        val live = MutableStateFlow(LiveText())
        val list = LazyListState(firstVisibleItemIndex = startIndex)
        var open by mutableStateOf(false)
        val scene = ImageComposeScene(900, 720, coroutineContext = clock.context) {
            AuroraTestTheme {
                HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                    AuroraHistory(
                        AiStudioScreenState(messages = newer + reply + older,
                            expandedWork = if (open) setOf(reply.id) else setOf()),
                        live, list, touch = false, onAction = { if (it is AiStudioAction.ToggleWork) open = !open },
                        padding = PaddingValues(48.dp), modifier = Modifier.fillMaxSize()
                            .background(HbAuroraColors.Canvas),
                    )
                }
            }
        }
        try {
            clock.frame(scene)
            val oldText = older.first().text
            val newText = newer.last().text
            val body = assertNotNull(scene.textNode(reply.text)).boundsInRoot
            val earlierBefore = assertNotNull(scene.textNode(oldText)).boundsInRoot
            val newTop = scene.textTop(newText)
            scene.moveTo(body.center)
            clock.reveal(scene)
            val headerPoint = Offset(body.center.x, body.top - 24f)
            scene.moveTo(headerPoint)
            val title = "Ход работы · 1 этап"
            val initialTitleTop = scene.textTop(title)
            assertTrue(earlierBefore.left < body.right && earlierBefore.bottom > initialTitleTop - 8f)
            val capture = startIndex == 0 && olderCount > 1
            if (capture) scene.capture("aurora-work-before-opening")
            scene.click(headerPoint)
            assertTrue(open)
            scene.verifyStationaryHeader(title, initialTitleTop, "Opening")
            val moving = scene.textTop(newText)
            val movingOlder = scene.textTop(oldText)
            if (capture) scene.capture("aurora-work-opens-downward")
            clock.advance(scene, 140)
            assertTrue(newTop < moving && moving < scene.textTop(newText))
            val earlier = assertNotNull(scene.textNode(oldText)).boundsInRoot
            val openedTitle = assertNotNull(scene.textNode(title)).boundsInRoot
            assertTrue(earlier.bottom <= openedTitle.top - 8f, "The opened header clears the preceding message")
            assertTrue(earlier.top < movingOlder && movingOlder < earlierBefore.top, "The preceding message rises")
            assertEquals(initialTitleTop, openedTitle.top, 1f)
            if (capture) scene.capture("aurora-work-opened-downward")
            scene.click(headerPoint)
            assertFalse(open, "The same screen coordinate still hits the header")
            scene.verifyStationaryHeader(title, initialTitleTop, "Closing")
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.verifyStationaryHeader(title: String, top: Float, transition: String) {
        repeat(6) { frame ->
            clock.advance(this, 16, passes = 1)
            assertEquals(top, textTop(title), 1f, "$transition frame $frame")
        }
    }

    private fun ImageComposeScene.textTop(text: String): Float = assertNotNull(textNode(text)).boundsInRoot.top

    @Test
    fun streamedLinesTakeTheirFinalSpaceBeforeTheFadeFinishes() {
        val older = User("older", "", "Предыдущая реплика")
        val reply = Reply("stream", "", status = ReplyStatus.Working)
        val stream = MutableStateFlow(LiveText(reply.id, "Первая строка"))
        val messages = listOf(older) + List(14) { index -> User("older-$index", "", "Реплика $index") }
        val list = LazyListState()
        val scene = ImageComposeScene(900, 540, coroutineContext = clock.context) {
            AuroraTestTheme {
                AuroraHistory(
                    AiStudioScreenState(messages = messages, live = reply), stream,
                    list, touch = false, onAction = {},
                    padding = PaddingValues(48.dp), modifier = Modifier.fillMaxSize().background(HbAuroraColors.Canvas),
                )
            }
        }
        try {
            clock.frame(scene)
            val original = assertNotNull(scene.textNode(older.text)).boundsInRoot.top
            val lineHeight = assertNotNull(scene.textNode("Первая строка")).boundsInRoot.height
            stream.value = stream.value.copy(text = "Первая строка\nВторая строка\nТретья строка")
            clock.frame(scene)
            assertEquals(original - lineHeight * 2, assertNotNull(scene.textNode(older.text)).boundsInRoot.top, 1f)
            clock.advance(scene, 60)
            val intermediate = assertNotNull(scene.textNode(older.text)).boundsInRoot.top
            assertEquals(original - lineHeight * 2, intermediate, 1f)
            stream.value = stream.value.copy(text = stream.value.text + "\nЧетвёртая строка")
            clock.frame(scene)
            assertEquals(original - lineHeight * 3, assertNotNull(scene.textNode(older.text)).boundsInRoot.top, 1f)
            clock.advance(scene, 60)
            scene.capture("aurora-stream-during-growth")
            clock.advance(scene, 140)
            assertEquals(original - lineHeight * 3, assertNotNull(scene.textNode(older.text)).boundsInRoot.top, 1f)

            list.requestScrollToItem(0, 32)
            clock.frame(scene)
            val readingPosition = assertNotNull(scene.textNode(older.text)).boundsInRoot.top
            stream.value = stream.value.copy(text = stream.value.text + "\nПятая строка\nШестая строка")
            clock.frame(scene)
            clock.advance(scene, 60)
            assertEquals(readingPosition, assertNotNull(scene.textNode(older.text)).boundsInRoot.top, 1f)
            clock.advance(scene, 80)
            assertEquals(readingPosition, assertNotNull(scene.textNode(older.text)).boundsInRoot.top, 1f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun finishingAStreamRetainsTheMeasuredReplyAndMovesItsNeighborsOnce() {
        val live = Reply("stream", "", status = ReplyStatus.Working)
        val older = User("older", "", "Предыдущая реплика")
        val messages = listOf(older) + List(14) { index -> User("older-$index", "", "Реплика $index") }
        val text = MutableStateFlow(LiveText(live.id, "Получен итог проверки."))
        var state by mutableStateOf(AiStudioScreenState(live = live, messages = messages))
        val scene = ImageComposeScene(900, 540, coroutineContext = clock.context) {
            AuroraTestTheme {
                AuroraHistory(
                    state, text, rememberLazyListState(), touch = false, onAction = {},
                    padding = PaddingValues(48.dp), modifier = Modifier.fillMaxSize(),
                )
            }
        }
        try {
            clock.frame(scene)
            val before = assertNotNull(scene.textNode(older.text)).boundsInRoot.top
            val finished = live.copy(
                status = ReplyStatus.Completed, text = text.value.text,
                result = ResultFile("report", "Результат.md", "Markdown", "Готово"),
            )
            state = state.copy(live = null, messages = listOf(finished) + messages)
            text.value = LiveText()
            clock.frame(scene)
            val placed = assertNotNull(scene.textNode(older.text)).boundsInRoot.top
            assertTrue(placed < before)
            clock.advance(scene, 180)
            val middle = assertNotNull(scene.textNode(older.text)).boundsInRoot.top
            clock.advance(scene, 200)
            val after = assertNotNull(scene.textNode(older.text)).boundsInRoot.top
            assertEquals(placed, middle, 1f)
            assertEquals(placed, after, 1f)
            assertTrue(scene.hasText("Результат.md"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun manualScrollReleasesTheCollapseInsetInGestureSizedSteps() {
        val reflow = AuroraHistoryReflow(LazyListState())
        reflow.apply(AuroraHistoryReflow.Adjustment("reply", expands = true, index = 0, offset = 0, tail = 60))

        assertEquals(Offset.Zero, reflow.onPreScroll(Offset(0f, -12f), NestedScrollSource.UserInput))
        assertEquals(60, reflow.tailPixels(null))
        assertEquals(Offset(0f, 8f), reflow.onPreScroll(Offset(0f, 8f), NestedScrollSource.UserInput))
        assertEquals(52, reflow.tailPixels(null))
        assertEquals(Offset(0f, 52f), reflow.onPreScroll(Offset(0f, 90f), NestedScrollSource.UserInput))
        assertEquals(0, reflow.tailPixels(null))
    }
}

private fun SemanticsNode.tailButton(): SemanticsNode? =
    if ("К новым сообщениям" in config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()) this
    else children.firstNotNullOfOrNull { it.tailButton() }
