@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraAgentMessage
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraMessageMenu
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.loadAuroraBackdrop
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class AuroraWorkAccessTest : AuroraWorkSceneTest() {

    @Test
    fun hoverHighlightsOnlyTheClickableWorkHeadingUnderThePointer() {
        val fixture = pencilTimelineFixture()
        val step = fixture.steps.first().copy(result = null)
        val staticStep = step.copy(id = "static", title = "Проверка завершена", events = listOf())
        val reply = fixture.copy(
            status = ReplyStatus.Completed, steps = listOf(step, staticStep),
            text = "Сначала проверяем состояние запроса. Новый запрос отправляем после подтверждения.",
        )
        val backdrop = runBlocking { loadAuroraBackdrop() }
        var workOpen by mutableStateOf(true)
        var stepsOpen by mutableStateOf(setOf<String>())
        val scene = ImageComposeScene(620, 350, coroutineContext = clock.context) {
            AuroraTestTheme {
                HbDesktopFocusIndication(
                    Modifier.fillMaxSize().auroraBackdrop(backdrop).padding(top = 60.dp, start = 20.dp),
                ) {
                    AuroraAgentMessage(reply, workOpen, stepsOpen, setOf(), touch = false, onAction = { action ->
                        when (action) {
                            AiStudioAction.ToggleWork(reply.id) -> workOpen = !workOpen
                            AiStudioAction.ToggleStep(reply.id, step.id) -> stepsOpen = setOf("${reply.id}/${step.id}")
                            else -> Unit
                        }
                    })
                }
            }
        }
        try {
            clock.frame(scene)
            val header = assertNotNull(scene.textNode("Ход работы · 2 этапа")).boundsInRoot
            val stage = assertNotNull(scene.textNode(step.title)).boundsInRoot
            val static = assertNotNull(scene.textNode(staticStep.title)).boundsInRoot
            val tailX = 500f
            val headerTail = Offset(tailX, header.center.y)
            val stageTail = Offset(tailX, stage.center.y)
            val staticTail = Offset(tailX, static.center.y)
            scene.assertHeadingHover(stage, headerTail, stageTail, staticTail)
            assertEquals(stage, assertNotNull(scene.textNode(step.title)).boundsInRoot)
            scene.click(stageTail)
            assertTrue(scene.hasText(checkNotNull(step.events.first().title)))
            scene.click(headerTail)
            assertFalse(workOpen)
            clock.advance(scene, 240)
            val collapsedTail = Offset(assertNotNull(scene.textNode(reply.text)).boundsInRoot.right - 8f, headerTail.y)
            scene.moveTo(collapsedTail)
            assertEquals(HbAuroraColors.WorkHover.toArgb(), scene.colorAt(collapsedTail))
            scene.click(collapsedTail)
            assertTrue(workOpen)
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.assertHeadingHover(
        stage: Rect, headerTail: Offset, stageTail: Offset, staticTail: Offset,
    ) {
        val idle = HbAuroraColors.AgentMessage.toArgb()
        val highlight = HbAuroraColors.WorkHover.toArgb()
        capture("aurora-work-headings-idle")
        moveTo(headerTail)
        capture("aurora-work-heading-hover")
        val headerColors = listOf(colorAt(headerTail), colorAt(stageTail))
        moveTo(stageTail)
        capture("aurora-stage-heading-hover")
        assertEquals(idle, colorAt(Offset(stage.left - 2f, stage.center.y)),
            "The marker has a gap before the hover fill")
        assertEquals(highlight, colorAt(Offset(stage.left + 2f, stage.center.y)))
        val stageColors = listOf(colorAt(headerTail), colorAt(stageTail))
        moveTo(staticTail)
        assertEquals(idle, colorAt(staticTail))
        assertEquals(idle, colorAt(stageTail))
        assertEquals(listOf(highlight, idle), headerColors)
        assertEquals(listOf(idle, highlight), stageColors)
    }

    @Test
    fun aFocusedToolCanStillBeUsedWhenItsStageProducesAnInterimResult() {
        val activeStage = pencilTimelineFixture().steps.last()
        val activeTool = activeStage.events.single()
        val title = checkNotNull(activeTool.title)
        val interim = "Результат этапа доступен"
        var reply by mutableStateOf(pencilTimelineFixture().copy(steps = listOf(activeStage)))
        var lastAction: AiStudioAction? = null
        val scene = ImageComposeScene(620, 350, coroutineContext = clock.context) {
            AuroraTestTheme {
                HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                    Column {
                        AuroraAgentMessage(
                            reply, false, setOf(), setOf(), touch = false,
                            onAction = { lastAction = it }, modifier = Modifier.width(520.dp),
                        )
                        Box(Modifier.size(44.dp).focusable())
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            scene.tap(Key.Tab)
            reply = reply.copy(steps = listOf(activeStage.copy(status = StepStatus.Done, result = interim)))
            clock.frame(scene)

            assertTrue(scene.hasText(title))
            assertTrue(scene.hasText(interim))
            scene.tap(Key.Enter)
            assertEquals(AiStudioAction.ToggleWorkEvent(reply.id, activeStage.id, activeTool.id), lastAction)

            scene.tap(Key.Tab)
            assertFalse(scene.hasText(title))
            assertTrue(scene.hasText(interim))
        } finally {
            scene.close()
        }
    }

    @Test
    fun historyAboveTheReplyIsClickableOnlyWhenShownAndRetainsKeyboardAccess() {
        var expanded by mutableStateOf(false)
        val reply = pencilTimelineFixture().copy(
            status = ReplyStatus.Completed,
            text = "Проверка завершена. Результаты остаются читаемыми.",
            steps = pencilTimelineFixture().steps.take(1),
        )
        val scene = ImageComposeScene(620, 350, coroutineContext = clock.context) {
            AuroraTestTheme {
                HbDesktopFocusIndication(Modifier.fillMaxSize().padding(top = 60.dp)) {
                    AuroraAgentMessage(
                        reply, expanded, setOf(), setOf(), touch = false,
                        onAction = { if (it == AiStudioAction.ToggleWork(reply.id)) expanded = !expanded },
                        modifier = Modifier.width(520.dp),
                    )
                }
            }
        }
        try {
            clock.frame(scene)
            scene.click(Offset(95f, 42f))
            assertFalse(expanded)

            scene.tap(Key.Tab)
            scene.tap(Key.Enter)
            assertTrue(expanded, "Tab reaches history while the pointer is outside the reply")
            scene.tap(Key.Spacebar)
            assertFalse(expanded, "Space closes history from the focused header")
            scene.moveTo(Offset(610f, 260f))
            scene.click(Offset(610f, 260f))
            clock.advance(scene, 500)
            clock.advance(scene, 240)
            assertEquals(0, scene.colorAt(Offset(95f, 42f)) ushr 24)

            scene.moveTo(Offset(95f, 78f))
            clock.advance(scene, 499)
            scene.click(Offset(95f, 42f))
            assertFalse(expanded)
            scene.moveTo(Offset(95f, 78f))
            clock.advance(scene, 500)
            scene.moveTo(Offset(95f, 42f))
            scene.click(Offset(95f, 42f))
            assertTrue(expanded)
            scene.click(Offset(95f, 42f))
            assertFalse(expanded, "The same header accepts a second click while opening")
            scene.click(Offset(95f, 42f))
            assertTrue(expanded)
            clock.advance(scene, 240)

            scene.click(Offset(95f, 42f))
            assertFalse(expanded)
            scene.tap(Key.Enter)
            assertTrue(expanded, "Enter reopens history after a pointer click closes it")
            scene.tap(Key.Spacebar)
            assertFalse(expanded, "The manual close keeps keyboard access on the collapsed header")
        } finally {
            scene.close()
        }
    }

    @Test
    fun pointerDisclosureDrawsOneContinuousUpperOutlineInBothDirections() {
        verifyPointerDisclosureOutline("Проверка завершена. Результат готов.", 28)
        verifyPointerDisclosureOutline("Проверка готова.", 48)
    }

    private fun verifyPointerDisclosureOutline(text: String, height: Int) {
        val reply = pencilTimelineFixture().run {
            copy(status = ReplyStatus.Completed, steps = steps.take(1), text = text)
        }
        var open by mutableStateOf(false)
        val scene = ImageComposeScene(620, 450, coroutineContext = clock.context) {
            AuroraTestTheme {
                HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(top = 60.dp)) {
                        AuroraAgentMessage(reply, open, setOf(), setOf(), false, { open = !open })
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            val body = assertNotNull(scene.textNode(reply.text)).boundsInRoot
            // The pointer can rest at the corner of the bubble's rectangular hover target.
            scene.moveTo(Offset(body.left - 6f, 62f))
            clock.advance(scene, 500)
            clock.advance(scene, 250)
            assertEquals(HbAuroraColors.WorkAccess.toArgb(), scene.colorAt(Offset(body.right + 5f, 44f)))
            scene.click(Offset(body.center.x, 46f))
            assertTrue(open)
            val left = body.left.toInt() - 8
            val allowedTop = (60 - height)..32
            assertEquals(32, scene.verifyOutlineDuringDisclosure(left, opening = true, allowedTop))
            val header = assertNotNull(scene.textNode("Ход работы · 1 этап")).boundsInRoot.center
            scene.moveTo(header)
            scene.click(header)
            assertFalse(open)
            assertEquals(60 - height, scene.verifyOutlineDuringDisclosure(left, opening = false, allowedTop))
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.verifyOutlineDuringDisclosure(
        left: Int,
        opening: Boolean,
        allowedTop: IntRange,
    ): Int {
        val direction = if (opening) "opening" else "closing"
        var previousTop = -1
        repeat(15) { frame ->
            clock.advance(this, 16)
            render(clock.nanoTime).use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    val top = (0 until 65).first { (bitmap.getColor(left + 64, it) ushr 24) > 127 }
                    assertTrue(top in allowedTop, "Unexpected header top at $direction frame $frame")
                    if (previousTop >= 0) {
                        assertTrue(
                            if (opening) top >= previousTop else top <= previousTop,
                            "The header moves continuously at $direction frame $frame",
                        )
                    }
                    previousTop = top
                    val edge = (top until top + 16).first { (bitmap.getColor(left + 1, it) ushr 24) > 127 }
                    assertTrue(edge > top, "The top corner is rounded at $direction frame $frame")
                    if (opening) {
                        assertEquals(
                            HbAuroraColors.AgentMessage.toArgb(), bitmap.getColor(left + 140, 59),
                            "There is no seam between the expanded header and its reply",
                        )
                    }
                    val hasFocusOutline = (top until 60).any {
                        bitmap.getColor(left + 64, it) == HbAuroraColors.Primary.toArgb()
                    }
                    assertFalse(hasFocusOutline, "Unexpected keyboard focus outline at $direction frame $frame")
                    assertTrue(
                        (edge until top + 37).all { (bitmap.getColor(left + 1, it) ushr 24) > 127 },
                        "The header and reply share one edge at $direction frame $frame",
                    )
                }
            }
            if (frame in setOf(0, 3, 6, 13)) capture("aurora-work-outline-$direction-$frame")
        }
        return previousTop
    }

    @Test
    fun selectingReplyTextKeepsTheRevealedHeaderVisible() {
        val reply = pencilTimelineFixture().copy(
            status = ReplyStatus.Completed,
            text = "Выделяем часть ответа для проверки поведения шапки.",
        )
        lateinit var selection: SelectionState
        val scene = ImageComposeScene(620, 350, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.padding(top = 60.dp)) {
                    selection = rememberSelectionState()
                    AuroraAgentMessage(reply, false, setOf(), setOf(), false, {}, selection = selection)
                }
            }
        }
        try {
            clock.frame(scene)
            val text = assertNotNull(scene.textNode(reply.text)).boundsInRoot
            val header = Offset(text.right - 16f, 44f)
            scene.moveTo(text.center)
            clock.reveal(scene)
            assertEquals(HbAuroraColors.WorkAccess.toArgb(), scene.colorAt(header))
            val start = Offset(text.left + 4f, text.center.y)
            scene.selectText(start, text.center)
            assertTrue(selection.selectedTexts.isNotEmpty(), "The gesture selects the reply text")
            scene.moveTo(Offset(610f, 260f))
            clock.advance(scene, 900)
            assertEquals(HbAuroraColors.WorkAccess.toArgb(), scene.colorAt(header))
            scene.capture("aurora-work-selection-keeps-header")
            selection.clear()
            clock.frame(scene)
            clock.advance(scene, 499)
            assertEquals(HbAuroraColors.WorkAccess.toArgb(), scene.colorAt(header))
            clock.advance(scene, 1)
            clock.advance(scene, 240)
            assertEquals(0, scene.colorAt(header) ushr 24)
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.selectText(start: Offset, end: Offset) {
        moveTo(start)
        sendPointerEvent(PointerEventType.Press, start, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Move, end, buttons = PointerButtons(isPrimaryPressed = true))
        sendPointerEvent(PointerEventType.Release, end, button = PointerButton.Primary)
        clock.frame(this)
    }

    @Test
    fun theWorkHeaderStaysOnTheBadgeAndWaitsToFadeWhenThePointerLeaves() {
        val fixture = pencilTimelineFixture()
        val reply = fixture.copy(status = ReplyStatus.Completed, text = "Проверка завершена для указанного диалога.")
        val scene = ImageComposeScene(900, 350, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.padding(top = 60.dp)) {
                    val selection = rememberSelectionState()
                    AuroraMessageMenu(reply, false, {}, 760.dp, selection) { hoverOverMenu ->
                        AuroraAgentMessage(
                            reply, false, setOf(), setOf(), false, {},
                            selection = selection, hoverOverMenu = hoverOverMenu,
                        )
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            val body = assertNotNull(scene.textNode(reply.text)).boundsInRoot
            val sample = Offset(body.right - 16, 44f)
            val hidden = scene.colorAt(sample)
            val visible = HbAuroraColors.WorkAccess.toArgb()
            val corner = Offset(body.left - 8f, 60f)
            val rounded = scene.cornerCoverage(corner)
            val badge = assertNotNull(scene.node {
                "Действия сообщения" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }).boundsInRoot
            scene.moveTo(body.center)
            clock.advance(scene, 500)
            clock.advance(scene, 60)
            val joining = scene.cornerCoverage(corner)
            scene.capture("aurora-header-joining")
            clock.advance(scene, 160)
            val joined = scene.cornerCoverage(corner)
            assertTrue(joining in (rounded + 1) until joined)
            scene.moveTo(badge.center)
            clock.advance(scene, 300)
            assertEquals(visible, scene.colorAt(sample))
            scene.moveTo(badge.center + Offset(0f, 11f))
            clock.advance(scene, 150)
            assertEquals(visible, scene.colorAt(sample))

            val outside = Offset(890f, 300f)
            scene.moveTo(outside)
            clock.advance(scene, 250)
            scene.moveTo(body.center)
            clock.advance(scene, 500)
            assertEquals(visible, scene.colorAt(sample))

            scene.verifyDelayedFade(outside, sample, corner, hidden, rounded, joined)
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.verifyDelayedFade(
        outside: Offset,
        sample: Offset,
        corner: Offset,
        hidden: Int,
        rounded: Int,
        joined: Int,
    ) {
        val visible = HbAuroraColors.WorkAccess.toArgb()
        moveTo(outside)
        clock.advance(this, 499)
        assertEquals(visible, colorAt(sample))
        clock.advance(this, 1)
        assertEquals(visible, colorAt(sample))
        clock.advance(this, 110)
        val fading = colorAt(sample)
        assertTrue(fading != visible && fading != hidden)
        assertTrue(cornerCoverage(corner) in (rounded + 1) until joined)
        capture("aurora-header-leaving")
        clock.advance(this, 150)
        assertEquals(hidden, colorAt(sample))
        assertEquals(rounded, cornerCoverage(corner))
    }

    private fun ImageComposeScene.tap(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        clock.frame(this)
    }

    private fun ImageComposeScene.colorAt(point: Offset): Int = render(clock.nanoTime).use { image ->
        Bitmap.makeFromImage(image).use { it.getColor(point.x.toInt(), point.y.toInt()) }
    }

    private fun ImageComposeScene.cornerCoverage(origin: Offset): Int = render(clock.nanoTime).use { image ->
        Bitmap.makeFromImage(image).use { bitmap ->
            val left = origin.x.toInt()
            val top = origin.y.toInt()
            (left until left + 16).sumOf { x ->
                (top until top + 16).count { y -> (bitmap.getColor(x, y) ushr 24) > 127 }
            }
        }
    }
}
