@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistory
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraTailFollow
import java.io.Closeable
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuroraThinkingScrollTest {
    @Test
    fun thinkingGrowthKeepsTheAnswerAndPaddingFixedAtTailAndPreservesReadingAbove() {
        ThinkingHistoryScene().use { fixture ->
            val clock = fixture.clock
            val scene = fixture.scene
            clock.frame(scene)
            clock.advance(scene, 200)
            val tail = fixture.position("Якорь", first = false)
            val answerTop = fixture.position("Ответ уже виден", first = true)
            val answer = fixture.pixels(answerTop)
            repeat(8) { index ->
                fixture.append("Новая строка $index")
                clock.frame(scene)
                assertEquals(tail, fixture.position("Якорь", first = false), 1f,
                    "A received thinking line must occupy its final height immediately")
                assertEquals(answer, fixture.pixels(answerTop), "Visible answer and its padding must stay fixed")
                assertFalse(fixture.list.canScrollBackward)
                clock.advance(scene, 16)
                assertEquals(tail, fixture.position("Якорь", first = false), 1f)
                assertEquals(48, fixture.list.layoutInfo.beforeContentPadding)
                assertEquals(48, fixture.list.layoutInfo.afterContentPadding)
            }
            fixture.tail.userScroll()
            fixture.list.requestScrollToItem(0, 64)
            clock.frame(scene)
            val reading = fixture.position("Якорь", first = true)
            repeat(3) {
                fixture.append("Продолжение при чтении истории")
                clock.frame(scene)
                assertEquals(reading, fixture.position("Якорь", first = true), 1f)
                clock.advance(scene, 32)
                assertEquals(reading, fixture.position("Якорь", first = true), 1f)
            }
            fixture.append("Финальный остаток", complete = true)
            clock.frame(scene)
            assertEquals(reading, fixture.position("Якорь", first = true), 1f)
            clock.advance(scene, 400)
            assertEquals(reading, fixture.position("Якорь", first = true), 1f)
            assertEquals(48, fixture.list.layoutInfo.beforeContentPadding)
        }
    }
}

private class ThinkingHistoryScene : Closeable {
    val clock = AuroraHoverTestClock()
    val list = LazyListState()
    val tail = AuroraTailFollow(list)
    private var reply by mutableStateOf(Reply("thinking-stream", "", text = "Ответ уже виден", steps = listOf(
        Step("read", "Проверка", "", StepStatus.Current, listOf(
            WorkEvent("thinking", "Думает...", "", "Якорь", WorkEventKind.Thinking, StepStatus.Current),
        )),
    )))
    private val stream = MutableStateFlow(LiveText(reply.id, reply.text))
    val scene = ImageComposeScene(900, 540, coroutineContext = clock.context) {
        AuroraTestTheme {
            AuroraHistory(
                AiStudioScreenState(live = reply, expandedEvents = setOf("${reply.id}/read/thinking"),
                    messages = List(14) { User("old-$it", "", "Предыдущая реплика $it") }),
                stream, list, false, {}, PaddingValues(48.dp),
                modifier = Modifier.fillMaxSize().background(HbAuroraColors.Canvas), tailFollow = tail,
            )
        }
    }

    fun append(line: String, complete: Boolean = false) {
        val step = reply.steps.single()
        val event = step.events.single()
        reply = reply.copy(steps = listOf(step.copy(events = listOf(event.copy(
            details = event.details + "\n" + line,
            status = if (complete) StepStatus.Done else StepStatus.Current,
        )))))
    }

    fun position(marker: String, first: Boolean): Float {
        val node = assertNotNull(scene.semanticsOwners.firstNotNullOfOrNull { owner ->
            owner.getAllSemanticsNodes(mergingEnabled = false).firstOrNull {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> marker in text.text }
            }
        })
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(assertNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)(layouts))
        val layout = layouts.single()
        val y = if (first) layout.getLineTop(0) else layout.getLineBottom(layout.lineCount - 1)
        return node.layoutInfo.coordinates.positionInRoot().y + y
    }

    fun pixels(top: Float): List<Int> = scene.render(clock.nanoTime).use { frame ->
        val bytes = assertNotNull(frame.encodeToData()).use { it.bytes }
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        image.getRGB(0, top.toInt(), image.width, 24, null, 0, image.width).toList()
    }

    override fun close() = scene.close()
}
