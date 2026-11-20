@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraAgentMessage
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraMessageMenu
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraUserBubble
import io.github.aequicor.heartbeat.features.aistudio.ui.components.OpenMessageMenuOnBadgeHover
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.loadAuroraBackdrop
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuroraMessageMenuTest {
    private val clock = AuroraHoverTestClock()
    private val prompt = User("prompt", "14:02", "Проверь контракты сессий и сценарий отмены")
    private val answer = Reply(
        "answer", "14:03", status = ReplyStatus.Completed,
        text = "Повторный запрос можно отправить после подтверждения остановки. " +
            "Иначе оба запроса могут выполняться одновременно.\n\n" +
            "Дождитесь статуса «Остановлено», затем отправьте запрос снова.",
    )

    @Test
    fun badgeOpensOnceWhenItBecomesInteractiveDuringHover() {
        val interactive = mutableStateOf(false)
        val hovered = mutableStateOf(true)
        var opens = 0
        val open: () -> Unit = { opens++ }
        val scene = ImageComposeScene(100, 100, coroutineContext = clock.context) {
            OpenMessageMenuOnBadgeHover(hovered.value, expanded = false, interactive.value, open)
        }
        try {
            scene.frame()
            assertEquals(0, opens)

            interactive.value = true
            scene.frame()
            assertEquals(1, opens)

            interactive.value = false
            scene.frame()
            interactive.value = true
            scene.frame()
            assertEquals(1, opens)

            hovered.value = false
            scene.frame()
            hovered.value = true
            scene.frame()
            assertEquals(2, opens)
        } finally {
            scene.close()
        }
    }

    @Test
    fun userMenuStaysAtTheInwardCornerAndRestoresFocusAfterEscape() {
        var received: AiStudioAction? = null
        val scene = messageScene(prompt, onAction = { received = it })
        try {
            scene.frame()
            val time = assertNotNull(scene.text("14:02")).boundsInWindow
            val body = assertNotNull(scene.text(prompt.text)).boundsInWindow
            val trigger = scene.trigger().boundsInWindow
            val sample = trigger.center + Offset(0f, -6f)
            val beforeHover = scene.colorAt(sample)
            val surfaceLeft = body.left - HbAuroraLayout.DesktopMessageHorizontalInset.value
            val surfaceBottom = time.bottom + HbAuroraLayout.DesktopMessageVerticalInset.value
            assertEquals(8f, surfaceLeft - trigger.center.x, absoluteTolerance = 1f)
            assertEquals(12f, surfaceBottom - trigger.center.y, absoluteTolerance = 1f)
            scene.click(trigger.center)
            assertNull(scene.text("Копировать"))
            scene.move(trigger.center)
            clock.advance(scene, 750)
            assertEquals(beforeHover, scene.colorAt(sample))

            val shown = scene.assertDelayedFadeIn(body.center, sample, beforeHover)
            scene.move(Offset(0f, 200f))
            clock.advance(scene, 110)
            val fadingOut = scene.colorAt(sample)
            assertNotEquals(shown, fadingOut)
            assertNotEquals(beforeHover, fadingOut)
            clock.advance(scene, 110)
            assertEquals(beforeHover, scene.colorAt(sample))

            scene.move(body.center)
            clock.reveal(scene)
            scene.save("aurora-user-message-trigger")
            scene.activateBadge()
            clock.advance(scene, 230)
            scene.save("aurora-user-message-menu")
            assertNotNull(scene.text("Редактировать"))
            assertEquals(time, assertNotNull(scene.text("14:02")).boundsInWindow)

            scene.press(Key.Escape)
            clock.advance(scene, 230)
            assertNull(scene.text("Редактировать"))
            scene.press(Key.Enter)
            assertNotNull(scene.text("Редактировать"))
            scene.press(Key.DirectionDown)
            scene.press(Key.Enter)
            assertEquals(AiStudioAction.EditMessage(prompt.id), received)
        } finally {
            scene.close()
        }
    }

    @Test
    fun badgeTurnsIntoTheHoverMenuAndReturnsOnlyOverTheBubble() {
        val scene = messageScene(prompt)
        try {
            scene.frame()
            val body = assertNotNull(scene.text(prompt.text)).boundsInWindow.center
            val badge = scene.trigger().boundsInWindow.center
            val sample = badge + Offset(0f, -6f)
            scene.move(body)
            clock.reveal(scene)
            val badgeColor = scene.colorAt(sample)
            scene.move(badge + Offset(0f, 11f))
            assertNotNull(scene.text("Редактировать"))
            val bounds = scene.menuBounds()
            assertTrue(bounds.contains(badge))
            val farCorner = Offset(bounds.left + 12f, bounds.bottom - 8f)
            val beforeMorph = scene.colorAt(farCorner)
            clock.advance(scene, 110)
            scene.save("aurora-message-menu-morph")
            clock.advance(scene, 120)
            assertNotEquals(beforeMorph, scene.colorAt(farCorner))
            scene.save("aurora-message-hover-menu")
            scene.move(bounds.center)
            clock.advance(scene, 250)
            assertNotNull(scene.text("Редактировать"))
            scene.move(body)
            clock.advance(scene, 230)
            assertNull(scene.text("Редактировать"))
            assertEquals(badgeColor, scene.colorAt(sample))
            scene.move(badge)
            clock.advance(scene, 230)
            assertNotNull(scene.text("Редактировать"))
            scene.move(scene.menuBounds().center)
            scene.move(Offset(0f, 210f))
            repeat(26) { clock.advance(scene, 16) }
            assertNull(scene.text("Редактировать"))
            assertNotEquals(badgeColor, scene.colorAt(sample))
        } finally {
            scene.close()
        }
    }

    @Test
    fun rightClickUsesThePointerAsTheAnchorAndKeepsTheMessageActions() {
        val scene = messageScene(prompt)
        try {
            scene.frame()
            val body = assertNotNull(scene.text(prompt.text)).boundsInWindow
            val point = Offset(body.left + 8f, body.center.y)
            scene.move(point)
            scene.sendPointerEvent(
                PointerEventType.Press, point, button = PointerButton.Secondary,
                buttons = PointerButtons(isSecondaryPressed = true),
            )
            scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Secondary)
            scene.frame()
            assertNotNull(scene.text("Копировать"))
            assertNotNull(scene.text("Редактировать"))
            val menu = scene.menuBounds()
            assertEquals(point.x, menu.left, 1f)
            assertEquals(point.y, menu.top, 1f)
            val sample = Offset(menu.right - 8f, menu.bottom - 8f)
            val hidden = scene.colorAt(sample)
            clock.advance(scene, 110)
            val fadingIn = scene.colorAt(sample)
            clock.advance(scene, 120)
            val shown = scene.colorAt(sample)
            assertNotEquals(hidden, fadingIn)
            assertNotEquals(shown, fadingIn)
            assertEquals(HbAuroraColors.MessageActionSurface.toArgb(), shown)
            scene.save("aurora-message-context-menu")
            scene.click(Offset(10f, 240f))
            clock.advance(scene, 110)
            val fadingOut = scene.colorAt(sample)
            assertNotEquals(shown, fadingOut)
            assertNotEquals(hidden, fadingOut)
            clock.advance(scene, 120)
            assertNull(scene.text("Редактировать"))
            assertEquals(hidden, scene.colorAt(sample))
        } finally {
            scene.close()
        }
    }

    @Test
    fun agentMenuFacesTheCenterAndNarrowTouchUsesTheSameActions() {
        val scene = messageScene(answer)
        try {
            scene.frame()
            val body = assertNotNull(scene.text(answer.text)).boundsInWindow
            val time = assertNotNull(scene.text("14:03")).boundsInWindow
            val trigger = scene.trigger().boundsInWindow
            assertEquals(
                8f, trigger.center.x - (time.right + HbAuroraLayout.DesktopMessageHorizontalInset.value),
                absoluteTolerance = 1f,
            )
            scene.move(body.center)
            clock.reveal(scene)
            scene.save("aurora-agent-message-trigger")
            scene.move(trigger.center)
            clock.advance(scene, 230)
            scene.save("aurora-agent-message-menu")
            assertNotNull(scene.text("Копировать"))
            assertNull(scene.text("Редактировать"))
        } finally {
            scene.close()
        }

        val narrow = messageScene(prompt, width = 390, touch = true)
        try {
            narrow.frame()
            val accessibleHold = assertNotNull(
                narrow.node { it.config.getOrNull(SemanticsActions.OnLongClick) != null },
            )
            assertEquals(true, accessibleHold.config[SemanticsActions.OnLongClick].action?.invoke())
            narrow.frame()
            assertNotNull(narrow.text("Редактировать"))
        } finally {
            narrow.close()
        }
    }

    @Test
    fun retryInANarrowDesktopViewportUsesTheOriginalMessage() {
        val failed = prompt.copy(delivery = Delivery.Failed, error = "Связь прервана")
        var received: AiStudioAction? = null
        val scene = messageScene(failed, width = 490, onAction = { received = it })
        try {
            scene.frame()
            val body = assertNotNull(scene.text(failed.text)).boundsInWindow
            scene.move(body.center)
            clock.reveal(scene)
            scene.activateBadge()
            assertNotNull(scene.text("Повторить отправку"))
            scene.press(Key.DirectionDown)
            scene.press(Key.DirectionDown)
            scene.press(Key.Enter)
            assertEquals(AiStudioAction.Retry(failed.id), received)
        } finally {
            scene.close()
        }
    }

    @Test
    fun messageOutlineUsesOneDpAtFractionalDesktopScale() {
        val scene = messageScene(answer, width = 1220, density = Density(1.25f))
        try {
            scene.frame()
            val body = assertNotNull(scene.text(answer.text)).boundsInWindow
            val x = body.center.x
            val outside = scene.colorAt(Offset(x, 24f))
            val outline = scene.colorAt(Offset(x, 25f))
            val coverage = scene.colorAt(Offset(x, 26f))
            val bodyColor = scene.colorAt(Offset(x, 27f))
            assertNotEquals(outside, outline)
            assertNotEquals(outline, coverage)
            assertNotEquals(coverage, bodyColor)
            val trigger = scene.trigger().boundsInWindow
            scene.move(body.center)
            clock.reveal(scene)
            scene.move(Offset(trigger.center.x - 7f, trigger.center.y))
            scene.save("aurora-message-outline-125")
        } finally {
            scene.close()
        }
    }

    private fun messageScene(
        message: Message,
        width: Int = 976,
        touch: Boolean = false,
        density: Density = Density(1f),
        onAction: (AiStudioAction) -> Unit = {},
    ): ImageComposeScene {
        val backdrop = runBlocking { loadAuroraBackdrop() }
        return ImageComposeScene(width, 280, density = density, coroutineContext = clock.context) {
            AuroraTestTheme(
                appearance = HbAppearance.Light,
                density = if (touch) HbDensity.Touch else HbDensity.Dense,
            ) {
                HbDesktopFocusIndication(
                    Modifier.fillMaxSize().auroraBackdrop(backdrop).padding(top = 20.dp),
                ) {
                    val selection = rememberSelectionState()
                    AuroraMessageMenu(message, touch, onAction, Dp.Infinity, selection) {
                        when (message) {
                            is User -> AuroraUserBubble(message, touch, onAction, selection)
                            is Reply -> AuroraAgentMessage(
                                message, false, setOf(), setOf(), touch, onAction, selection = selection,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun ImageComposeScene.assertDelayedFadeIn(body: Offset, sample: Offset, hidden: Int): Int {
        move(body)
        clock.advance(this, 499)
        assertEquals(hidden, colorAt(sample))
        move(Offset(0f, 200f))
        clock.advance(this, 250)
        assertEquals(hidden, colorAt(sample))

        move(body)
        clock.advance(this, 500)
        assertEquals(hidden, colorAt(sample))
        clock.advance(this, 110)
        val fading = colorAt(sample)
        assertNotEquals(hidden, fading)
        clock.advance(this, 110)
        return colorAt(sample).also { assertNotEquals(fading, it) }
    }

    private fun ImageComposeScene.save(name: String) {
        val directory = File("build/screenshots").apply { mkdirs() }
        render(clock.nanoTime).use { screenshot ->
            screenshot.encodeToData(EncodedImageFormat.PNG)?.use {
                File(directory, "$name.png").writeBytes(it.bytes)
            }
        }
    }

    private fun ImageComposeScene.trigger() = assertNotNull(node {
        "Действия сообщения" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    })

    private fun ImageComposeScene.menuBounds() =
        assertNotNull(node { it.config.contains(SemanticsProperties.CollectionInfo) }).boundsInWindow

    private fun ImageComposeScene.activateBadge() {
        assertEquals(true, trigger().config[SemanticsActions.OnClick].action?.invoke())
        frame()
    }

    private fun ImageComposeScene.text(value: String) =
        node { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }

    private fun ImageComposeScene.node(where: (SemanticsNode) -> Boolean): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(where) }

    private fun SemanticsNode.find(where: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (where(this)) this else children.firstNotNullOfOrNull { it.find(where) }

    private fun ImageComposeScene.move(point: Offset) {
        sendPointerEvent(PointerEventType.Move, point)
        frame()
    }

    private fun ImageComposeScene.click(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        frame()
    }

    private fun ImageComposeScene.press(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        frame()
    }

    private fun ImageComposeScene.frame() = clock.frame(this)

    private fun ImageComposeScene.colorAt(point: Offset): Int = render(clock.nanoTime).use { image ->
        Bitmap.makeFromImage(image).use { it.getColor(point.x.toInt(), point.y.toInt()) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class AuroraHoverTestClock {
    private val scheduler = TestCoroutineScheduler()
    val context = StandardTestDispatcher(scheduler)
    val nanoTime: Long get() = scheduler.currentTime * 1_000_000L

    fun frame(scene: ImageComposeScene, passes: Int = 3) {
        repeat(passes) {
            scheduler.runCurrent()
            scene.render(nanoTime).close()
        }
    }

    fun advance(scene: ImageComposeScene, ms: Long, passes: Int = 3) {
        scheduler.advanceTimeBy(ms)
        frame(scene, passes)
    }

    fun reveal(scene: ImageComposeScene) {
        advance(scene, 500)
        advance(scene, 220)
    }
}
