@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.testing.createAiStudioTestStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraAgentMessage
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistory
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkTimeline
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuroraDisclosureStabilityTest {
    private val clock = AuroraHoverTestClock()

    @Test
    fun aCompletedAnswerFitsExpandedHistoryWithinTheAvailableWidth() {
        val answer = pencilTimelineFixture().copy(
            status = ReplyStatus.Completed, time = "14:12", text = "Проверка завершена.",
        )
        listOf(false, true).forEach { touch ->
            var open by mutableStateOf(false)
            var details by mutableStateOf(false)
            var availableWidth by mutableStateOf(650.dp)
            var measuredWidth = 0
            val scene = ImageComposeScene(650, 720, coroutineContext = clock.context) {
                AuroraTestTheme(density = if (touch) HbDensity.Touch else HbDensity.Dense) {
                    AuroraAgentMessage(
                        answer, open, if (details) setOf("pencil-work/repeat") else setOf(),
                        if (details) setOf("pencil-work/repeat/contract") else setOf(), touch, onAction = {},
                        modifier = Modifier.widthIn(max = availableWidth)
                            .onSizeChanged { measuredWidth = it.width },
                    )
                }
            }
            try {
                clock.frame(scene)
                val collapsedWidth = measuredWidth
                assertTrue(collapsedWidth in 1..300)
                open = true
                clock.frame(scene)
                val expandedWidth = measuredWidth
                assertTrue(expandedWidth in collapsedWidth + 1..HbAuroraLayout.AgentBubble.value.toInt())
                details = true
                clock.frame(scene)
                if (touch) assertTrue(measuredWidth in expandedWidth..HbAuroraLayout.AgentBubble.value.toInt())
                else assertEquals(expandedWidth, measuredWidth, "Opening details preserves desktop work width")
                assertNotNull(scene.textNode("Читаю контракт сессии"))
                availableWidth = 160.dp
                clock.frame(scene)
                assertEquals(160, measuredWidth)
                availableWidth = 650.dp
                open = false
                clock.frame(scene)
                clock.advance(scene, 300)
                assertEquals(collapsedWidth, measuredWidth)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun openingThinkingDoesNotWidenTheRecoveryReply() = runBlocking<Unit> {
        val store = createAiStudioTestStore(AuroraTestPreferences, this)
        try {
            val state = withTimeout(5_000) { store.screenState.first { !it.loading } }
            val reply = state.messages.filterIsInstance<Reply>().single { it.id == "sample-recovery" }
            val step = reply.steps.single()
            val tool = step.events.single { it.kind == WorkEventKind.File }
            val thinking = step.events.single { it.kind == WorkEventKind.Thinking }
            val stepKey = "${reply.id}/${step.id}"
            var expandedEvents by mutableStateOf(setOf<String>())
            var measuredWidth = 0
            val scene = ImageComposeScene(652, 520, coroutineContext = clock.context) {
                AuroraTestTheme(appearance = HbAppearance.Light) {
                    Box(Modifier.fillMaxSize().background(HbAuroraColors.ChatBackdrop).padding(10.dp, 36.dp)) {
                        AuroraAgentMessage(
                            reply, true, setOf(stepKey), expandedEvents, false, onAction = {},
                            modifier = Modifier.onSizeChanged { measuredWidth = it.width },
                        )
                    }
                }
            }
            try {
                clock.frame(scene)
                val widthWithPreview = measuredWidth
                assertEquals(HbAuroraLayout.AgentBubble.value.toInt(), widthWithPreview,
                    "Expanded desktop work fills the available reply width")
                scene.capture("aurora-recovery-thinking-collapsed")
                val previewStyle = scene.textStyle(thinking.preview)
                expandedEvents = setOf("$stepKey/${thinking.id}")
                clock.frame(scene)
                repeat(18) {
                    clock.advance(scene, 16)
                    assertEquals(widthWithPreview, measuredWidth)
                }
                val details = assertNotNull(scene.textNode(thinking.detailText)).boundsInRoot
                assertTrue(details.height > 22f)
                scene.capture("aurora-recovery-thinking-expanded")
                expandedEvents = expandedEvents + "$stepKey/${tool.id}"
                clock.frame(scene)
                clock.advance(scene, 240)
                scene.capture("aurora-recovery-work-type")

                val messageStyle = scene.textStyle(reply.text)
                assertEquals(AuroraTestFonts.ui, messageStyle.fontFamily)
                assertEquals(15.sp, messageStyle.fontSize)
                assertEquals(22.sp, messageStyle.lineHeight)
                assertPreviewStyle(previewStyle)
                listOf("Ход работы · 1 этап", step.title, checkNotNull(tool.title), checkNotNull(thinking.title))
                    .forEach { scene.assertWorkStyle(it, 14, 20, FontWeight.Normal) }
                listOf(tool.detailText, thinking.detailText)
                    .forEach { scene.assertWorkStyle(it, 14, 20, FontWeight.Normal) }
                scene.assertWorkStyle(checkNotNull(tool.detailLabel), 14, 20, FontWeight.Normal)
                scene.assertWorkStyle("Готово", 12, 16, FontWeight.Normal)
            } finally {
                scene.close()
            }
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    private fun assertPreviewStyle(style: TextStyle) {
        assertEquals(AuroraTestFonts.ui, style.fontFamily)
        assertEquals(13.sp, style.fontSize)
        assertEquals(18.sp, style.lineHeight)
        assertEquals(FontWeight.Normal, style.fontWeight)
        assertEquals(HbAuroraColors.DesktopWorkMuted, style.color)
    }

    private fun ImageComposeScene.assertWorkStyle(text: String, size: Int, lineHeight: Int, weight: FontWeight) {
        val style = textStyle(text)
        assertEquals(AuroraTestFonts.ui, style.fontFamily, "$text uses the system UI font")
        assertEquals(size.sp, style.fontSize, "$text keeps its semantic size")
        assertEquals(lineHeight.sp, style.lineHeight, "$text keeps its semantic line height")
        assertEquals(weight, style.fontWeight, "$text keeps its semantic weight")
    }

    @Test
    fun inlineWorkControlsStayUnderThePointerOnDesktopAndAndroid() {
        listOf(false, true).forEach(::verifyInlineWorkControls)
    }

    @Test
    fun pastStageUsesCompactDesktopHeightAndCentersTheMarkerWhenTextGrows() {
        val sample = pencilTimelineFixture()
        val step = sample.steps.first()
        val reply = sample.copy(steps = listOf(step))
        var touch by mutableStateOf(false)
        var textScale by mutableFloatStateOf(1f)
        val scene = ImageComposeScene(360, 300, coroutineContext = clock.context) {
            AuroraTestTheme(textScale = textScale, density = if (touch) HbDensity.Touch else HbDensity.Dense) {
                AuroraWorkTimeline(reply, setOf(), setOf(), touch, onAction = {})
            }
        }
        try {
            clock.frame(scene)
            val header = assertNotNull(scene.textNode(step.title)).boundsInRoot
            assertEquals(28f, header.height)

            textScale = 2f
            clock.frame(scene)
            val tallHeader = assertNotNull(scene.textNode(step.title)).boundsInRoot
            assertTrue(tallHeader.height > header.height)
            scene.render(clock.nanoTime).use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    val markerX = (HbAuroraLayout.ContentInset + HbAuroraLayout.WorkAxis).value.toInt()
                    val marker = bitmap.getColor(markerX, tallHeader.center.y.toInt())
                    assertEquals(HbAuroraColors.StageComplete.toArgb(), marker)
                }
            }

            textScale = 1f
            touch = true
            clock.frame(scene)
            assertEquals(48f, assertNotNull(scene.textNode(step.title)).boundsInRoot.height)
        } finally {
            scene.close()
        }
    }

    @Test
    fun toolHeaderStaysStillAsShortAndroidHistoryStartsScrolling() {
        val sample = pencilTimelineFixture()
        val step = sample.steps.first()
        val tool = step.events.first().copy(details = "# Архитектура\n\nГраница UI и логики.\n\n" +
            "История хранится по диалогам. Остановка подтверждается отдельным событием.")
        val reply = sample.copy(
            status = ReplyStatus.Stopped,
            steps = listOf(step.copy(events = listOf(tool) + step.events.drop(1),
                result = "Проверка исходных условий завершена. Повтор можно привязать к исходной реплике " +
                    "и сохранить позицию пользователя во время получения ответа.")),
            text = "Работа остановлена. Полученные данные сохранены.",
        )
        val list = androidx.compose.foundation.lazy.LazyListState()
        val live = MutableStateFlow(LiveText())
        var state by mutableStateOf(AiStudioScreenState(
            messages = listOf(reply, User("before", "14:04", "Aurora audit retry")),
            expandedSteps = setOf("${reply.id}/${step.id}"),
        ))
        val scene = ImageComposeScene(390, 844, coroutineContext = clock.context) {
            AuroraTestTheme(density = HbDensity.Touch) {
                AuroraHistory(
                    state, live, list, touch = true, onAction = { state = state.toggleDisclosure(it) },
                    padding = PaddingValues(top = 166.dp, bottom = 74.dp), modifier = Modifier.fillMaxSize(),
                )
            }
        }
        try {
            clock.frame(scene)
            assertTrue(!list.canScrollBackward && !list.canScrollForward)
            scene.toggleAtFixedPoint(checkNotNull(tool.title), checkNotNull(step.events[1].title), opens = true)
            assertTrue(list.canScrollBackward)
            scene.toggleAtFixedPoint(checkNotNull(tool.title), checkNotNull(step.events[1].title), opens = false)
        } finally {
            scene.close()
        }
    }

    private fun verifyInlineWorkControls(touch: Boolean) {
        val reply = pencilTimelineFixture().run {
            copy(
                status = ReplyStatus.Completed, steps = steps.take(1),
                text = "Сначала запрашиваем состояние текущего хода. Пока сервер не ответил, " +
                    "показываем «Проверяем состояние» и сохраняем черновик.",
            )
        }
        val newer = List(10) { User("below-$it", "", "Нижняя реплика $it") }
        val older = List(20) { User("above-$it", "", "Верхняя реплика $it") }
        val next = newer.last().text
        val live = MutableStateFlow(LiveText())
        var state by mutableStateOf(AiStudioScreenState(
            messages = newer + reply + older, expandedWork = setOf(reply.id),
        ))
        val scene = ImageComposeScene(900, 900, coroutineContext = clock.context) {
            AuroraTestTheme(density = if (touch) HbDensity.Touch else HbDensity.Dense) {
                HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                    AuroraHistory(
                        state, live, rememberLazyListState(), touch,
                        onAction = { state = state.toggleDisclosure(it) }, padding = PaddingValues(48.dp),
                        modifier = Modifier.fillMaxSize().background(HbAuroraColors.Canvas),
                    )
                }
            }
        }
        try {
            clock.frame(scene)
            if (touch) {
                scene.toggleAtFixedPoint("Ход работы · 1 этап", next, opens = false)
                scene.toggleAtFixedPoint("Ход работы · 1 этап", next, opens = true)
            }
            val step = reply.steps.single()
            scene.toggleAtFixedPoint(step.title, next, opens = true)
            for (event in listOf(step.events.first(), step.events.last())) {
                val title = checkNotNull(event.title)
                val first = event == step.events.first()
                val following = checkNotNull(if (first) step.events[1].title else step.result)
                val thinking = event.kind == WorkEventKind.Thinking
                val capture = when {
                    touch -> null
                    first -> "aurora-inline-disclosure"
                    else -> "aurora-thinking-disclosure"
                }
                scene.toggleAtFixedPoint(
                    title, following, opens = true, capturePrefix = capture, anchorHeader = thinking,
                )
                if (thinking) {
                    assertNull(scene.textNode(event.preview))
                    assertNotNull(scene.textNode(event.detailText))
                }
                scene.toggleAtFixedPoint(title, following, opens = false, anchorHeader = thinking)
                if (thinking) assertNotNull(scene.textNode(event.preview))
            }
            scene.toggleAtFixedPoint(step.title, next, opens = false)
        } finally {
            scene.close()
        }
    }

    private fun AiStudioScreenState.toggleDisclosure(action: AiStudioAction): AiStudioScreenState = when (action) {
        is AiStudioAction.ToggleWork -> copy(expandedWork = expandedWork.flip(action.id))
        is AiStudioAction.ToggleStep -> copy(expandedSteps = expandedSteps.flip("${action.replyId}/${action.stepId}"))
        is AiStudioAction.ToggleWorkEvent -> copy(
            expandedEvents = expandedEvents.flip("${action.replyId}/${action.stepId}/${action.eventId}"),
        )
        else -> this
    }

    private fun Set<String>.flip(id: String) = if (id in this) this - id else this + id

    private fun ImageComposeScene.anchorBounds(title: String, anchorHeader: Boolean) =
        assertNotNull(textNode(title)).let { node ->
            if (anchorHeader) generateSequence(node) { it.parent }.first {
                it.config.contains(SemanticsProperties.StateDescription)
            } else node
        }.boundsInRoot

    private fun ImageComposeScene.toggleAtFixedPoint(
        title: String, next: String, opens: Boolean, capturePrefix: String? = null, anchorHeader: Boolean = false,
    ) {
        val startingTitle = assertNotNull(textNode(title)).boundsInRoot
        assertTrue(startingTitle.height > 0f, "$title must be in view, bounds=$startingTitle")
        val anchor = anchorBounds(title, anchorHeader)
        val startingNext = textTop(next)
        moveTo(startingTitle.center)
        if (capturePrefix != null) capture("$capturePrefix-before")
        click(startingTitle.center)
        if (anchorHeader && capturePrefix != null) capture("$capturePrefix-start")
        repeat(16) {
            clock.advance(this, 16, passes = 1)
            val bounds = anchorBounds(title, anchorHeader)
            assertEquals(anchor.top, bounds.top, 1f, "$title remains under the pointer on frame $it")
            assertEquals(anchor.left, bounds.left, 1f, "$title keeps its horizontal position")
            if (anchorHeader && capturePrefix != null && it == 2) capture("$capturePrefix-fade")
            if (anchorHeader && capturePrefix != null && it == 4) capture("$capturePrefix-middle")
        }
        val movement = textTop(next) - startingNext
        assertTrue(if (opens) movement > 0 else movement < 0, "$title moves the following reply, delta=$movement")
        if (capturePrefix != null) capture("$capturePrefix-after")
    }

    private fun ImageComposeScene.textTop(text: String): Float = assertNotNull(textNode(text)).boundsInRoot.top

    private fun ImageComposeScene.textStyle(text: String): TextStyle {
        val config = semanticsOwners.asSequence().flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.config
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) == true)
        return layouts.single().layoutInput.style
    }

    private fun ImageComposeScene.textNode(text: String): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.findText(text) }

    private fun SemanticsNode.findText(text: String): SemanticsNode? =
        if (config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }) this
        else children.firstNotNullOfOrNull { it.findText(text) }

    private fun ImageComposeScene.moveTo(point: Offset) {
        sendPointerEvent(PointerEventType.Move, point)
        clock.frame(this)
    }

    private fun ImageComposeScene.click(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        clock.frame(this)
    }

    private fun ImageComposeScene.capture(name: String) {
        val directory = File("build/screenshots").apply { mkdirs() }
        render(clock.nanoTime).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use {
                File(directory, "$name.png").writeBytes(it.bytes)
            }
        }
    }
}
