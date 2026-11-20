@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbProvideWindowCaption
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Answer
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.FileGroup
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.FileRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Question
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Request
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.di.DefaultAiStudioStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraAgentMessage
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraFiles
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistory
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraRequestPanel
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraViewer
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraHistoryEdges
import io.github.aequicor.heartbeat.features.aistudio.ui.components.loadAuroraBackdrop
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiStudioScreenScreenshotTest {
    @Test
    fun desktopHistoryAndFiles() {
        screenshot("aurora-desktop", 1440, 900, workspace, HbDensity.Dense)
        val caption = HbWindowCaption(138.dp, 32.dp) { _, _ -> }
        val files = workspace.copy(filesVisible = true)
        val desktop = ImageIO.read(
            screenshot("aurora-desktop-files", 1440, 900, files, HbDensity.Dense, caption = caption),
        )
        val surface = HbAuroraColors.DesktopCanvas.toArgb()
        assertEquals(surface, desktop.getRGB(1408, 22))
        assertEquals(surface, desktop.getRGB(1391, 52))
        assertTrue((1400..1415).any { x -> (44..59).any { y -> desktop.getRGB(x, y) != surface } })
        assertEquals(HbAuroraColors.FileTypeSurface.toArgb(), desktop.getRGB(1140, 142))
        val android = ImageIO.read(renderScreenshot("aurora-android-files", 390, 844) {
            AuroraTestTheme(appearance = HbAppearance.Light, density = HbDensity.Touch) {
                AuroraFiles(files, touch = true, onAction = {}, Modifier.fillMaxSize())
            }
        })
        assertTrue((344..373).any { x -> (18..37).any { y -> android.getRGB(x, y) != surface } })
        assertEquals(HbAuroraColors.FileTypeSurface.toArgb(), android.getRGB(28, 128))
        val dialogClock = AuroraHoverTestClock()
        val filesScreen = ImageIO.read(renderScreenshot(
            "aurora-android-files-screen", 390, 844, clock = dialogClock,
            beforeCapture = { dialogClock.advance(this, 400) },
        ) {
            AuroraTestTheme(appearance = HbAppearance.Light, density = HbDensity.Touch) {
                AiStudioScreen(
                    files, MutableStateFlow(Draft(files.chat?.id)), MutableStateFlow(LiveText()),
                    null, {}, {}, avatarVisible = false,
                )
            }
        })
        assertEquals(surface, filesScreen.getRGB(195, 7))
        assertNotEquals(surface, filesScreen.getRGB(195, 8))
        assertNotEquals(surface, filesScreen.getRGB(195, 9))
        assertEquals(surface, filesScreen.getRGB(195, 10))
        assertEquals(HbAuroraColors.FileTypeSurface.toArgb(), filesScreen.getRGB(38, 138))
    }

    @Test
    fun touchFileScreenReturnsToTheWorkspaceWithoutDiscardingTheDraft() {
        val clock = AuroraHoverTestClock()
        val state = mutableStateOf(workspace.copy(filesVisible = true))
        val draft = MutableStateFlow(Draft(workspace.chat?.id, "Черновик"))
        val stream = MutableStateFlow(LiveText())
        val actions = mutableListOf<AiStudioAction>()
        val scene = ImageComposeScene(390, 844, coroutineContext = clock.context) {
            AuroraTestTheme(appearance = HbAppearance.Light, density = HbDensity.Touch) {
                AiStudioScreen(state.value, draft, stream, null, { action ->
                    if (action is AiStudioAction.ToggleFiles) {
                        actions.add(action)
                        state.value = state.value.copy(filesVisible = action.visible)
                    }
                }, {}, avatarVisible = false)
            }
        }
        try {
            clock.advance(scene, 400)
            fun hasControl(label: String) = scene.semanticsOwners.any {
                it.rootSemanticsNode.hasAccessibleControl(label)
            }
            assertTrue(hasControl("Вернуться к диалогу"))
            assertFalse(hasControl("Отправить сообщение"))
            val back = Offset(42f, 38f)
            scene.sendPointerEvent(PointerEventType.Press, back, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, back, type = PointerType.Touch)
            clock.advance(scene, 400)
            assertEquals(AiStudioAction.ToggleFiles(false), actions.single())
            assertFalse(hasControl("Вернуться к диалогу"))
            assertTrue(hasControl("Отправить сообщение"))
            assertEquals("Черновик", draft.value.text)
        } finally {
            scene.close()
        }
    }

    @Test
    fun desktopHistoryBlursBothEdgesWithoutDimmingTheTransition() {
        val image = ImageIO.read(renderScreenshot("aurora-history-edges", 160, 180) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().auroraHistoryEdges(56, 56, 160.dp)) {
                    Box(Modifier.fillMaxHeight().width(80.dp).background(Color.White))
                }
            }
        })
        for (y in listOf(0, 16, 40, 56, 90, 124, 140, 164, 179)) {
            val brightness = image.getRGB(24, y) and 0xff
            assertTrue(brightness >= 253, "The opaque message must cover the background at y=$y, got $brightness")
        }
        assertTrue((image.getRGB(80, 16) and 0xff) in 70..180, "The top edge blurs content")
        assertTrue((image.getRGB(80, 164) and 0xff) in 70..180, "The bottom edge blurs content")
        assertEquals(Color.Black.toArgb(), image.getRGB(80, 90), "The reading area stays sharp")
    }

    @Test
    fun reportViewerPlacesItsCloseButtonBelowTheWindowsCaption() {
        val desktop = ImageIO.read(viewerScreenshot(touch = false))
        val surface = HbAuroraColors.ViewerSurface.toArgb()
        for (y in 0 until 32) {
            assertEquals(surface, desktop.getRGB(1408, y), "The viewer leaves the native caption controls clear")
        }
        assertTrue((54..66).any { desktop.getRGB(1408, it) != surface }, "The viewer close button is below the caption")
        val dividerColors = setOf(0xFFE6EBE5.toInt(), 0xFFE6EBE6.toInt())
        assertEquals(surface, desktop.getRGB(720, 86))
        assertTrue((87..88).all { desktop.getRGB(720, it) in dividerColors })

        val android = ImageIO.read(viewerScreenshot(touch = true))
        assertTrue((22..34).any { android.getRGB(354, it) != surface }, "Android uses its full client area")
        assertTrue((55..56).all { android.getRGB(195, it) in dividerColors })
    }

    @Test
    fun reportViewerTemporarilyHidesTheWorkspaceFromAccessibility() {
        val clock = AuroraHoverTestClock()
        val state = mutableStateOf(workspace)
        val draft = MutableStateFlow(Draft(workspace.chat?.id, "Черновик"))
        val stream = MutableStateFlow(LiveText())
        val scene = ImageComposeScene(960, 600, coroutineContext = clock.context) {
            AuroraTestTheme(appearance = HbAppearance.Light, density = HbDensity.Dense) {
                AiStudioScreen(state.value, draft, stream, null, {}, {}, avatarVisible = false)
            }
        }
        try {
            fun hasControl(label: String) = scene.semanticsOwners.any {
                it.rootSemanticsNode.hasAccessibleControl(label)
            }
            clock.frame(scene)
            assertTrue(hasControl("Отправить сообщение"))
            state.value = workspace.copy(viewer = file)
            clock.advance(scene, 400)
            assertTrue(hasControl("Закрыть просмотр"))
            assertFalse(hasControl("Отправить сообщение"))
            state.value = workspace
            clock.advance(scene, 400)
            assertFalse(hasControl("Закрыть просмотр"))
            assertTrue(hasControl("Отправить сообщение"))
            assertEquals("Черновик", draft.value.text)
        } finally {
            scene.close()
        }
    }

    private fun SemanticsNode.hasAccessibleControl(label: String): Boolean {
        if (config.contains(SemanticsProperties.HideFromAccessibility)) return false
        return label in config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() ||
            children.any { it.hasAccessibleControl(label) }
    }

    private fun viewerScreenshot(touch: Boolean): File {
        val clock = AuroraHoverTestClock()
        val caption = if (touch) HbWindowCaption(0.dp, 0.dp) { _, _ -> } else HbWindowCaption(138.dp, 32.dp) { _, _ -> }
        return renderScreenshot(
            if (touch) "aurora-viewer-android" else "aurora-viewer-windows",
            if (touch) 390 else 1440, if (touch) 844 else 900,
            beforeCapture = { clock.advance(this, 400) }, clock = clock,
        ) {
            AuroraTestTheme(
                appearance = HbAppearance.Light,
                density = if (touch) HbDensity.Touch else HbDensity.Dense,
            ) {
                HbProvideWindowCaption(caption) { AuroraViewer(file, touch) {} }
            }
        }
    }

    @Test
    fun desktopFigmaParityFixture() = runBlocking<Unit> {
        val store = DefaultAiStudioStoreFactory(AuroraTestPreferences).create(this)
        try {
            val site = "project:Сайт проекта"
            withTimeout(5_000) { store.screenState.first { !it.loading } }
            store.dispatch(AiStudioAction.ToggleNavigation(site))
            val state = withTimeout(5_000) {
                store.screenState.first { state -> state.navigation.any { it.id == site && it.expanded == false } }
            }
            val font = AuroraTestFonts.ui
            val scrollIndex = state.messages.indexOfFirst { it.id == "sample-dedup" }
            val matched = state.copy(
                chat = state.chat?.copy(access = Access.Full),
                expandedWork = setOf("sample-recovery"),
                expandedSteps = setOf("sample-recovery/check"),
                expandedEvents = setOf("sample-recovery/check/thinking"),
                restoreIndex = scrollIndex.coerceAtLeast(0),
                restoreAnchor = AiStudioScreenState.Anchor("sample-dedup", 78),
            )
            screenshot("aurora-desktop-parity", 1440, 900, matched, HbDensity.Dense, font)
            val narrowChat = matched.chat?.copy(access = Access.Workspace, contextPercent = null, quota = null)
            screenshot("aurora-desktop-narrow-header", 328, 750, matched.copy(chat = narrowChat), HbDensity.Dense, font)
            val windows = ImageIO.read(screenshot(
                "aurora-windows-parity", 1440, 900, matched, HbDensity.Dense, font,
                HbWindowCaption(138.dp, 32.dp) { _, _ -> },
            ))
            assertWindowsNavigation(windows)
            val touchMessages = state.messages.filter { it.id == "sample-task" || it.id == "sample-reply" }
            val touch = state.copy(chat = matched.chat, messages = touchMessages)
            val android = ImageIO.read(
                screenshot("aurora-android-header-parity", 390, 844, touch, HbDensity.Touch, font),
            )
            assertEquals(HbAuroraColors.Canvas.toArgb(), android.getRGB(0, 420))
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    private fun assertWindowsNavigation(windows: java.awt.image.BufferedImage) {
        val sidebarBackground = HbAuroraColors.DesktopCanvas.toArgb()
        val navigationRight = HbAuroraLayout.NavigationWidth.value.toInt()
        assertEquals(sidebarBackground, windows.getRGB(0, 450))
        assertEquals(sidebarBackground, windows.getRGB(HbAuroraLayout.SidebarWithGap.value.toInt() + 3, 450))
        assertEquals(sidebarBackground, windows.getRGB(400, 4))
        val createBackground = HbAuroraColors.NavigationPrimaryAction.toArgb()
        assertEquals(sidebarBackground, windows.getRGB(190, 51))
        assertEquals(createBackground, windows.getRGB(190, 52))
        assertEquals(createBackground, windows.getRGB(navigationRight - 21, 66))
        assertEquals(sidebarBackground, windows.getRGB(navigationRight - 20, 66))
        assertEquals(HbAuroraColors.NavigationSearchOutline.toArgb(), windows.getRGB(navigationRight - 12, 66))
        assertEquals(HbAuroraColors.NavigationSearchSurface.toArgb(), windows.getRGB(navigationRight - 8, 66))
        assertEquals(createBackground, windows.getRGB(190, 79))
        assertEquals(sidebarBackground, windows.getRGB(190, 80))
        val selectedBackground = HbAuroraColors.NavigationSelected.toArgb()
        val rowGap = HbAuroraLayout.NavigationRowGap.value.toInt()
        assertEquals(sidebarBackground, windows.getRGB(200, 121 + rowGap))
        assertEquals(selectedBackground, windows.getRGB(200, 122 + rowGap))
        assertEquals(selectedBackground, windows.getRGB(200, 145 + rowGap))
        assertEquals(sidebarBackground, windows.getRGB(200, 146 + rowGap))
        val childShift = rowGap * 3
        assertEquals(HbAuroraColors.SessionUnread.toArgb(), windows.getRGB(navigationRight, 190 + childShift))
        assertNotEquals(sidebarBackground, windows.getRGB(31, 181 + childShift))
        assertEquals(sidebarBackground, windows.getRGB(31, 189 + childShift),
            "The branch leaves the square corner clear")
        assertTrue((186..188).any { windows.getRGB(34, it + childShift) != sidebarBackground }, "The branch curves")
        assertNotEquals(sidebarBackground, windows.getRGB(43, 189 + childShift))
    }

    @Test
    fun desktopHistoryUsesShortGapsOnlyForRelatedMessages() {
        val messages = listOf(
            Reply(
                "newest", "14:03", text = "Итог проверки сценариев.",
                status = ReplyStatus.Completed, promptId = "p2",
            ),
            Reply(
                "related", "14:02", text = "Первый результат проверки.",
                status = ReplyStatus.Completed, promptId = "p2",
            ),
            Reply(
                "earlier", "13:59", text = "Результат предыдущей задачи.",
                status = ReplyStatus.Completed, promptId = "p1",
            ),
        )
        val background = Color.Magenta
        val live = MutableStateFlow(AiStudioScreenState.LiveText())
        val image = ImageIO.read(renderScreenshot("aurora-message-spacing", 600, 320) {
            AuroraTestTheme(appearance = HbAppearance.Light, density = HbDensity.Dense) {
                AuroraHistory(
                    workspace.copy(messages = messages), live, rememberLazyListState(), touch = false,
                    onAction = {}, padding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize().background(background),
                )
            }
        })
        fun filled(y: Int) = image.getRGB(72, y) != background.toArgb()
        val rows = 1 until image.height
        val starts = rows.filter { filled(it) && !filled(it - 1) }
        val ends = rows.filter { !filled(it) && filled(it - 1) }
        assertEquals(
            listOf(HbAuroraLayout.DesktopMessageGap.value.toInt(), HbAuroraLayout.RelatedMessageGap.value.toInt()),
            starts.drop(1).zip(ends).map { (start, end) -> start - end },
        )
    }

    @Test
    fun nativeCaptionReservesOnlyItsCornerAndKeepsSettingsInteractive() {
        val regions = mutableMapOf<Any, Rect>()
        val registered = mutableSetOf<Rect>()
        val caption = HbWindowCaption(138.dp, 32.dp) { key, rect ->
            if (rect == null) {
                regions.remove(key)
            } else {
                regions[key] = rect
                registered.add(rect)
            }
        }
        val desktop = screenshot("aurora-windows", 1440, 900, workspace, HbDensity.Dense, AuroraTestFonts.ui, caption)
        assertTrue(registered.any { it.contains(Offset(127f, 28f)) })
        assertTrue(registered.any { it.contains(Offset(450f, 24f)) })
        assertTrue(registered.none { it.contains(Offset(197f, 22f)) })
        assertTrue(regions.isEmpty())
        val canvas = HbAuroraColors.DesktopCanvas.toArgb()
        val image = ImageIO.read(desktop)
        assertEquals(canvas, image.getRGB(1390, 22))
        assertNotEquals(canvas, image.getRGB(1200, 22))
        assertNotEquals(canvas, image.getRGB(1390, 70))

        val compact = screenshot("aurora-windows-narrow", 500, 600, workspace, HbDensity.Dense, caption = caption)
        val compactImage = ImageIO.read(compact)
        assertEquals(canvas, compactImage.getRGB(490, 22))
        assertNotEquals(canvas, compactImage.getRGB(175, 25))
        val tiny = screenshot("aurora-windows-compact", 345, 450, workspace, HbDensity.Dense, caption = caption)
        val tinyImage = ImageIO.read(tiny)
        assertEquals(canvas, tinyImage.getRGB(120, 22))
        assertNotEquals(canvas, tinyImage.getRGB(120, 50))
    }

    @Test
    fun leftNativeCaptionKeepsChatAndControlsOutsideTheSystemArea() {
        val registered = mutableSetOf<Rect>()
        val caption = HbWindowCaption(0.dp, 32.dp, leftInset = 72.dp) { _, rect ->
            if (rect != null) registered.add(rect)
        }
        val canvas = HbAuroraColors.DesktopCanvas.toArgb()
        val wide = ImageIO.read(
            screenshot("aurora-macos", 1440, 900, workspace, HbDensity.Dense, caption = caption),
        )
        assertEquals(canvas, wide.getRGB(40, 22))
        assertNotEquals(canvas, wide.getRGB(400, 22))

        registered.clear()
        val compact = ImageIO.read(
            screenshot("aurora-macos-narrow", 500, 600, workspace, HbDensity.Dense, caption = caption),
        )
        assertEquals(canvas, compact.getRGB(40, 22))
        assertNotEquals(canvas, compact.getRGB(450, 22))
        assertNotEquals(canvas, compact.getRGB(40, 93))
        assertTrue(registered.none { it.overlaps(Rect(0f, 0f, 72f, 32f)) })
        assertTrue(registered.any { it.contains(Offset(155f, 24f)) })
    }

    @Test
    fun desktopPencilWorkTimeline() {
        val font = AuroraTestFonts.ui
        val reply = pencilTimelineFixture()
        renderScreenshot("aurora-work-parity", 680, 426) {
            AuroraTestTheme(appearance = HbAppearance.Light, fontFamily = font) {
                Box {
                    AuroraAgentMessage(
                        reply, expanded = false, expandedSteps = setOf("pencil-work/repeat"),
                        expandedEvents = setOf("pencil-work/repeat/contract"), touch = false,
                        onAction = {}, modifier = Modifier.width(632.dp),
                    )
                }
            }
        }
    }

    @Test
    fun desktopPencilWorkAccess() = runBlocking<Unit> {
        val store = DefaultAiStudioStoreFactory(AuroraTestPreferences).create(this)
        try {
            val state = withTimeout(5_000) { store.screenState.first { !it.loading } }
            val reply = state.messages.filterIsInstance<Reply>().single { it.id == "sample-recovery" }
            val font = AuroraTestFonts.ui
            val content: @Composable () -> Unit = {
                AuroraTestTheme(appearance = HbAppearance.Light, fontFamily = font) {
                    Box(Modifier.fillMaxSize().background(HbAuroraColors.WorkAccess).padding(top = 32.dp)) {
                        AuroraAgentMessage(reply, false, setOf(), setOf(), false, {}, Modifier.width(613.dp))
                    }
                }
            }
            renderScreenshot("aurora-work-access-rest", 613, 160, content = content)
            val clock = AuroraHoverTestClock()
            renderScreenshot("aurora-work-access-hover", 613, 160, clock = clock, beforeCapture = {
                sendPointerEvent(PointerEventType.Move, Offset(300f, 74f))
                clock.frame(this)
                clock.reveal(this)
            }, content = content)
            val short = state.messages.filterIsInstance<Reply>().single { it.id == "sample-draft" }
            renderScreenshot("aurora-message-short-time", 230, 70) {
                AuroraTestTheme(appearance = HbAppearance.Light, fontFamily = font) {
                    Box(Modifier.fillMaxSize().background(HbAuroraColors.WorkAccess)) {
                        AuroraAgentMessage(short, false, setOf(), setOf(), false, {})
                    }
                }
            }
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun desktopCompletedReplyBeforeAndAfterDisclosure() = runBlocking {
        val store = DefaultAiStudioStoreFactory(AuroraTestPreferences).create(this)
        try {
            val state = withTimeout(5_000) { store.screenState.first { !it.loading } }
            val reply = state.messages.filterIsInstance<Reply>().single { it.id == "sample-draft" }
            val font = AuroraTestFonts.ui
            listOf(false, true).forEach { expanded ->
                val name = if (expanded) "aurora-answer-width-expanded" else "aurora-answer-width-collapsed"
                renderScreenshot(name, 660, 250) {
                    AuroraTestTheme(appearance = HbAppearance.Light, fontFamily = font) {
                        Box(Modifier.fillMaxSize().background(HbAuroraColors.ChatBackdrop).padding(top = 52.dp)) {
                            AuroraAgentMessage(reply, expanded, setOf(), setOf(), false, {})
                        }
                    }
                }
            }
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun androidLayoutWithReducedImeViewportAndStrictRequest() {
        val request = Request.Permission("permission", "Разрешить запись отчёта?", "aurora-report.md",
            "Подготовить отчёт по проверенным сценариям", "Только этот демонстрационный шаг")
        screenshot("aurora-touch", 390, 844, workspace, HbDensity.Touch)
        screenshot("aurora-touch-request", 390, 536, workspace.copy(request = request), HbDensity.Touch)
    }

    @Test
    fun desktopDecisionPanels() {
        val font = AuroraTestFonts.ui
        val first = Question(
            "save", "Что сохранять после отмены?", listOf("Текст и вложения", "Только текст"), freeText = true,
        )
        val questions = Request.Questions(
            "draft", listOf(first, Question("next", "Другой вопрос", freeText = true)),
            answers = mapOf(first.id to Answer(setOf("Текст и вложения"), "Сохранять до повторного запуска")),
            remaining = 2f / 3f, timed = true,
        )
        val permission = Request.Permission(
            "patch", "Применить подготовленное исправление?", "SessionController.kt", "SessionControllerTest.kt", "",
        )
        renderScreenshot("aurora-questions-parity", 648, 215) {
            AuroraTestTheme(appearance = HbAppearance.Light, density = HbDensity.Dense, fontFamily = font) {
                AuroraRequestPanel(questions, false, {}, inlineStop = false)
            }
        }
        renderScreenshot("aurora-permission-parity", 648, 163) {
            AuroraTestTheme(appearance = HbAppearance.Light, density = HbDensity.Dense, fontFamily = font) {
                AuroraRequestPanel(permission, false, {}, inlineStop = false)
            }
        }
        val request = Request.Permission(
            "test", "Разрешить запуск проверки?", ":features:aistudio:desktopTest", "", "",
        )
        screenshot("aurora-request-desktop", 1440, 900, workspace.copy(request = request), HbDensity.Dense, font)
    }

    @Test
    fun desktopComposerStates() {
        val font = AuroraTestFonts.ui
        val caption = HbWindowCaption(138.dp, 32.dp) { _, _ -> }
        screenshot("aurora-composer-running", 1440, 900, workspace.copy(activity = Activity.Running),
            HbDensity.Dense, font, caption)
        screenshot("aurora-composer-draft", 1440, 900, workspace.copy(attachments = listOf(file)),
            HbDensity.Dense, font, caption,
            draftText = "Проверь сценарии отмены и восстановления.\nСохрани результат в отчёте.\nПроверь повтор.")
    }

    private fun screenshot(
        name: String, width: Int, height: Int, state: AiStudioScreenState, density: HbDensity, font: FontFamily? = null,
        caption: HbWindowCaption? = null,
        draftText: String = "",
    ): File {
        val backdrop = runBlocking { loadAuroraBackdrop() }
        return renderScreenshot(name, width, height) {
            val content: @Composable () -> Unit = {
                AuroraTestTheme(appearance = HbAppearance.Light, density = density, fontFamily = font) {
                    val draft = MutableStateFlow(Draft(state.chat?.id, draftText))
                    val stream = MutableStateFlow(LiveText())
                    AiStudioScreen(state, draft, stream, backdrop, {}, {}, avatarVisible = true)
                }
            }
            if (caption == null) content() else HbProvideWindowCaption(caption, content)
        }
    }

    private fun renderScreenshot(
        name: String,
        width: Int,
        height: Int,
        beforeCapture: ImageComposeScene.() -> Unit = {},
        clock: AuroraHoverTestClock? = null,
        content: @Composable () -> Unit,
    ) = runBlocking {
        val scene = ImageComposeScene(
            width, height, density = Density(1f), coroutineContext = clock?.context ?: coroutineContext,
        ) { content() }
        try {
            if (clock == null) repeat(3) { scene.render().close() } else clock.frame(scene)
            scene.beforeCapture()
            val screenshot = if (clock == null) scene.render() else scene.render(clock.nanoTime)
            val data = screenshot.use { assertNotNull(it.encodeToData(EncodedImageFormat.PNG)) }
            val directory = File("build/screenshots").apply { mkdirs() }
            val file = File(directory, "$name.png")
            file.writeBytes(assertNotNull(data.bytes))
            assertTrue(file.length() > 0)
            file
        } finally {
            scene.close()
        }
    }

    private val file = AiStudioScreenState.File("report", "aurora-report.md", "Markdown", "# Рабочий отчёт")
    private val chat = Chat("chat", "Единая модель сессий", "Heartbeat", contextPercent = 61)
    private val workspace = AiStudioScreenState(
        loading = false,
        chat = chat,
        chats = listOf(
            chat, Chat("long", "Длинная история", "Heartbeat"), Chat("research", "Рабочие заметки", "Проекты"),
        ),
        messages = listOf(
            Reply("r1", "14:03", listOf(Step("review", "Проверены контракты", "Мок-данные", StepStatus.Done)),
                text = "Повторный запрос можно отправить после подтверждения остановки.\n\n" +
                    "Черновик и вложения сохраняются.", status = ReplyStatus.Completed, result = file),
            User("u1", "14:02", "Проверь контракты сессий и сценарий отмены"),
            Reply("r2", "13:58", text = "Начну с границ рабочей области.", status = ReplyStatus.Completed),
            User("u2", "13:53", "Проверь, как восстанавливается позиция чтения в длинной истории"),
        ),
        fileGroups = listOf(
            FileGroup(
                "month:2026:11", "Ноябрь",
                listOf(
                    FileRow(
                        file.id, file.name, "md",
                        "${file.content.encodeToByteArray().size} Б", "14 ноя, 14:02",
                    ),
                ),
            ),
        ),
    )
}
