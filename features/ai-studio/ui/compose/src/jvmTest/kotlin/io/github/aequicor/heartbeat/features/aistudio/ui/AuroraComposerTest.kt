@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Request
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraLowerControls
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.ui.components.preserveReadingOnComposerResize
import java.io.File as LocalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat

class AuroraComposerTest {
    private val chat = Chat("chat", "Новый диалог", "Heartbeat")

    @Test
    fun keyboardFocusDrawsTheInnerOutlineWithoutMovingTheEditor() {
        val state = AiStudioScreenState(chat = chat)
        val scene = composerScene(MutableStateFlow(Draft(chat.id)), { state }, {})
        try {
            scene.frame()
            val bounds = assertNotNull(scene.desc("Сообщение")).boundsInRoot
            assertEquals(HbAuroraColors.ComposerSurface.toArgb(), scene.composerPixel(380, 2))
            repeat(2) {
                scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown))
                scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyUp))
                scene.frame()
            }
            assertEquals(true, scene.desc("Сообщение")?.config?.getOrNull(SemanticsProperties.Focused))
            assertEquals(bounds, scene.desc("Сообщение")?.boundsInRoot)
            assertEquals(HbAuroraColors.Primary.toArgb(), scene.composerPixel(380, 2))
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.composerPixel(x: Int, y: Int): Int = render().use { image ->
        Bitmap.makeFromImage(image).use { it.getColor(x, y) }
    }

    @Test
    fun textEditsAreVisibleBeforeTheDraftSnapshotArrives() {
        val state = AiStudioScreenState(chat = chat)
        val draft = MutableStateFlow(Draft(chat.id))
        val edits = mutableListOf<AiStudioAction>()
        val scene = composerScene(draft, { state }, { edits.add(it) })
        try {
            scene.frame()
            scene.edit("AB")
            scene.edit("ABC")
            assertEquals("ABC", scene.desc("Сообщение")?.config?.getOrNull(SemanticsProperties.EditableText)?.text)
            assertEquals(
                listOf<AiStudioAction>(
                    AiStudioAction.EditDraft(chat.id, "AB"),
                    AiStudioAction.EditDraft(chat.id, "ABC"),
                ),
                edits,
            )
            draft.value = Draft(chat.id, "ABC")
            scene.frame()
            draft.value = Draft(chat.id)
            scene.frame()
            assertEquals("", scene.desc("Сообщение")?.config?.getOrNull(SemanticsProperties.EditableText)?.text)
        } finally {
            scene.close()
        }
    }

    @Test
    fun theDesktopComposerKeepsItsGeometryAndSelectionThroughARequest() {
        var state by mutableStateOf(AiStudioScreenState(chat = chat))
        val draft = MutableStateFlow(Draft("chat"))
        val actions = mutableListOf<AiStudioAction>()
        val scene = composerScene(draft, { state }, onAction = { action ->
            if (action is AiStudioAction.EditDraft) draft.value = Draft(action.chatId, action.text)
            actions.add(action)
        })
        try {
            scene.frame()
            val attach = assertNotNull(scene.desc("Прикрепить файл")).boundsInRoot
            val send = assertNotNull(scene.desc("Отправить сообщение"))
            val field = assertNotNull(scene.desc("Сообщение")).boundsInRoot
            assertEquals(32f, attach.height)
            assertEquals(8f, attach.left)
            assertEquals(field.bottom + HbAuroraLayout.ControlGap.value, attach.bottom)
            assertEquals(32f, send.boundsInRoot.height)
            assertEquals(720f, send.boundsInRoot.left)
            assertEquals(attach.top, send.boundsInRoot.top)
            assertNotNull(send.config.getOrNull(SemanticsProperties.Disabled))

            scene.click(assertNotNull(scene.desc("Сообщение")).boundsInRoot.center)
            scene.pressEnter()
            assertFalse(AiStudioAction.Send in actions)
            scene.edit("Проверка")
            assertEquals(HbAuroraColors.DesktopPrimaryAction.toArgb(), scene.composerPixel(736, 14))
            scene.pressEnter(shift = true)
            assertEquals("Проверка\n", draft.value.text)
            assertTrue(assertNotNull(scene.desc("Отправить сообщение")).boundsInRoot.top > attach.top)

            val setSelection = assertNotNull(scene.desc("Сообщение")).config.getOrNull(SemanticsActions.SetSelection)
            assertTrue(assertNotNull(setSelection?.action)(1, 4, false))
            scene.frame()
            assertEquals(
                TextRange(1, 4), scene.desc("Сообщение")?.config?.getOrNull(SemanticsProperties.TextSelectionRange),
            )
            state = state.copy(request = Request.Permission("request", "Разрешить?", "Файл", "Отчёт", "Один шаг"))
            scene.frame()
            assertNull(scene.desc("Сообщение"))
            state = state.copy(request = null)
            scene.frame()
            val restored = assertNotNull(scene.desc("Сообщение")).config
            assertEquals(TextRange(1, 4), restored.getOrNull(SemanticsProperties.TextSelectionRange))
            scene.click(assertNotNull(scene.desc("Сообщение")).boundsInRoot.center)
            scene.pressEnter()
            assertEquals(1, actions.count { it == AiStudioAction.Send })
        } finally {
            scene.close()
        }
    }

    @Test
    fun attachmentControlOpensItsMenuOnlyOnClick() {
        val file = File("brief", "architecture.md", "Markdown", "# Архитектура")
        val state = AiStudioScreenState(chat = chat, fileChoices = listOf(file))
        val clock = AuroraHoverTestClock()
        val scene = composerScene(MutableStateFlow(Draft(chat.id)), { state }, {}, clock.context)
        try {
            clock.frame(scene)
            val attach = assertNotNull(scene.desc("Прикрепить файл")).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Move, attach)
            clock.frame(scene)
            clock.advance(scene, 1_000)
            assertNull(scene.text("Прикрепить файл"))
            assertNull(scene.text(file.name))

            scene.sendPointerEvent(PointerEventType.Press, attach, button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, attach, button = PointerButton.Primary)
            clock.frame(scene)
            assertNotNull(scene.text(file.name))
            assertNull(scene.text("Прикрепить файл"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun anAttachmentCanBeSentAndStopWaitsForAcknowledgment() {
        val file = File("brief", "architecture.md", "Markdown", "# Архитектура")
        var state by mutableStateOf(AiStudioScreenState(chat = chat, fileChoices = listOf(file)))
        val actions = mutableListOf<AiStudioAction>()
        val scene = composerScene(MutableStateFlow(Draft(chat.id)), { state }, actions::add)
        try {
            scene.frame()
            scene.click(assertNotNull(scene.desc("Прикрепить файл")).boundsInRoot.center)
            scene.click(assertNotNull(scene.text(file.name)).boundsInWindow.center)
            assertEquals(AiStudioAction.Attach(file.id), actions.last())

            state = state.copy(attachments = listOf(file))
            scene.frame()
            assertNotNull(scene.desc("Убрать ${file.name}"))
            scene.click(assertNotNull(scene.desc("Отправить сообщение")).boundsInRoot.center)
            assertEquals(AiStudioAction.Send, actions.last())

            state = state.copy(activity = Activity.Running)
            scene.frame()
            scene.click(assertNotNull(scene.desc("Остановить работу")).boundsInRoot.center)
            assertEquals(AiStudioAction.CancelTurn, actions.last())

            state = state.copy(activity = Activity.Stopping)
            scene.frame()
            val stop = assertNotNull(scene.desc("Остановить работу"))
            assertNotNull(stop.config.getOrNull(SemanticsProperties.Disabled))
            assertNotNull(scene.text("Останавливаем…"))
            scene.click(stop.boundsInRoot.center)
            assertEquals(1, actions.count { it == AiStudioAction.CancelTurn })
        } finally {
            scene.close()
        }
    }

    @Test
    fun growingAndShrinkingTheComposerKeepAnOlderMessageAtTheSameHeight() {
        var bottom by mutableIntStateOf(72)
        lateinit var list: LazyListState
        val scene = ImageComposeScene(420, 520) {
            list = rememberLazyListState(30, 16)
            LazyColumn(Modifier.fillMaxSize(), state = list, reverseLayout = true,
                contentPadding = PaddingValues(bottom = bottom.dp)) {
                items(90, key = { it }) { index ->
                    Box(Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "Сообщение $index" })
                }
            }
        }
        try {
            scene.frame()
            val previous = assertNotNull(scene.desc("Сообщение 37")).boundsInRoot.top
            list.preserveReadingOnComposerResize(bottom, 148)
            bottom = 148
            scene.frame()
            assertEquals(previous, assertNotNull(scene.desc("Сообщение 37")).boundsInRoot.top, 1f)
            list.preserveReadingOnComposerResize(bottom, 72)
            bottom = 72
            scene.frame()
            assertEquals(previous, assertNotNull(scene.desc("Сообщение 37")).boundsInRoot.top, 1f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun showingTheJumpButtonDoesNotMoveHistoryOnDesktopOrTouch() {
        listOf(false, true).forEach { touch ->
            val clock = AuroraHoverTestClock()
            val messages = List(30) { index -> User("message-$index", "", "Реплика $index") }
            val state = AiStudioScreenState(chat = chat, messages = messages)
            val draft = MutableStateFlow(Draft(chat.id))
            val live = MutableStateFlow(LiveText())
            val scene = ImageComposeScene(if (touch) 390 else 720, 620, coroutineContext = clock.context) {
                AuroraTestTheme(density = if (touch) HbDensity.Touch else HbDensity.Dense) {
                    AuroraWorkspace(
                        state, draft, live, backdrop = null, touch = touch, onAction = {},
                        showNavigation = false, modifier = Modifier.fillMaxSize(),
                        captionOverlap = if (touch) 0.dp else 96.dp,
                        caption = HbWindowCaption(0.dp, if (touch) 0.dp else 31.dp) { _, _ -> },
                    )
                }
            }
            try {
                clock.frame(scene)
                val label = messages.first().text
                val initial = assertNotNull(scene.text(label)).boundsInRoot.top
                val scroll = assertNotNull(scene.node {
                    it.boundsInRoot.contains(Offset(180f, 300f)) &&
                        it.config.getOrNull(SemanticsActions.ScrollBy) != null
                }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action)
                assertNull(scene.desc("К новым сообщениям"))
                if (!touch) scene.verifyDesktopTrack(clock.nanoTime)
                assertTrue(scroll(0f, 12f))
                repeat(24) { clock.advance(scene, 16) }
                assertNotNull(scene.desc("К новым сообщениям"))
                assertEquals(initial + 12f, assertNotNull(scene.text(label)).boundsInRoot.top, 1f)
            } finally {
                scene.close()
            }
        }
    }

    private fun ImageComposeScene.verifyDesktopTrack(time: Long) {
        val track = assertNotNull(desc("Прокрутка истории")).boundsInRoot
        assertEquals(70f, track.top, 1f)
        assertEquals(590f, track.bottom, 1f)
        val directory = LocalFile("build/screenshots").apply { mkdirs() }
        render(time).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use {
                LocalFile(directory, "aurora-chat-longer-scrollbar.png").writeBytes(it.bytes)
            }
        }
    }

    private fun ImageComposeScene.edit(text: String) {
        val edit = assertNotNull(desc("Сообщение")).config.getOrNull(SemanticsActions.SetText)?.action
        assertTrue(assertNotNull(edit)(AnnotatedString(text)))
        frame()
    }

    private fun composerScene(
        draft: MutableStateFlow<Draft>,
        state: () -> AiStudioScreenState,
        onAction: (AiStudioAction) -> Unit,
        context: CoroutineContext = Dispatchers.Unconfined,
    ) = ImageComposeScene(760, 520, coroutineContext = context) {
        AuroraTestTheme(appearance = HbAppearance.Light) {
            HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                AuroraLowerControls(
                    state(), draft, rememberLazyListState(), false, false, onAction, maxPanelHeight = 380.dp,
                )
            }
        }
    }

    private fun ImageComposeScene.desc(label: String): SemanticsNode? =
        node { label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    private fun ImageComposeScene.text(label: String): SemanticsNode? =
        node { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } }

    private fun ImageComposeScene.node(where: (SemanticsNode) -> Boolean): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(where) }

    private fun SemanticsNode.find(where: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (where(this)) this else children.firstNotNullOfOrNull { it.find(where) }

    private fun ImageComposeScene.click(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        frame()
    }

    private fun ImageComposeScene.pressEnter(shift: Boolean = false) {
        sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown, isShiftPressed = shift))
        sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp, isShiftPressed = shift))
        frame()
    }

    private fun ImageComposeScene.frame() = repeat(3) { render().close() }
}
