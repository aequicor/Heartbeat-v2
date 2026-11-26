@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraDesktopSidebar
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraFiles
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHeader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat

class AuroraDesktopHoverTest {
    private val clock = AuroraHoverTestClock()
    private val hoverColor = HbAuroraColors.NavigationHover.toArgb()

    @Test
    fun sidebarHoverFillsProjectRowThroughItsIndependentAction() {
        val state = AiStudioScreenState(
            navigation = listOf(
                NavigationRow("project", "Проверки", NavigationKind.Project, "Проверки"),
                NavigationRow("ungrouped", "Без проекта", NavigationKind.Section, "Без проекта"),
            ),
        )
        val received = mutableListOf<AiStudioAction>()
        val expansion = mutableListOf<Pair<String, Boolean>>()
        val projectsSessions = object : TestProjectsSessionsComponent() {
            override fun setProjectExpanded(id: String, expanded: Boolean) {
                expansion.add(id to expanded)
            }
        }
        val scene = ImageComposeScene(
            HbAuroraLayout.SidebarWithGap.value.toInt(), 160, coroutineContext = clock.context,
        ) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                HbDesktopFocusIndication(Modifier.fillMaxSize().background(HbAuroraColors.Canvas)) {
                    AuroraDesktopSidebar(
                        state, received::add, projectsSessions, Modifier.width(HbAuroraLayout.SidebarWithGap),
                    )
                }
            }
        }
        try {
            clock.frame(scene)
            val action = assertNotNull(scene.description("Новый диалог · Проверки")).boundsInRoot
            val startInset = Offset(20f, action.center.y)
            val endInset = Offset(action.right - 4f, action.center.y)
            scene.move(startInset)
            assertEquals(hoverColor, scene.colorAt(startInset))
            assertEquals(hoverColor, scene.colorAt(endInset))
            scene.capture("aurora-sidebar-cell-hover")

            scene.move(endInset)
            assertEquals(hoverColor, scene.colorAt(startInset))
            scene.click(startInset)
            scene.click(action.center)
            val ungrouped = assertNotNull(scene.description("Новый диалог · Без проекта")).boundsInRoot
            assertEquals(28f, ungrouped.height)
            scene.click(ungrouped.center)
            assertEquals(listOf("Проверки" to true), expansion)
            assertEquals(
                listOf<AiStudioAction>(
                    AiStudioAction.CreateSessionInProject("Проверки"),
                    AiStudioAction.CreateSessionInProject("Без проекта"),
                ),
                received,
            )
            scene.move(Offset(HbAuroraLayout.NavigationWidth.value + 6f, action.center.y))
            assertEquals(HbAuroraColors.Canvas.toArgb(), scene.colorAt(startInset))
        } finally {
            scene.close()
        }
    }

    @Test
    fun archiveAppearsOnHoverWithoutMovingTheStatusAndDispatchesOnlyThatChat() {
        val chat = NavigationRow("chat", "Сессия", NavigationKind.Chat, "Проверки", status = NavigationStatus.Unread)
        val state = AiStudioScreenState(navigation = listOf(chat))
        val actions = mutableListOf<AiStudioAction>()
        val scene = ImageComposeScene(
            HbAuroraLayout.SidebarWithGap.value.toInt(), 160, coroutineContext = clock.context,
        ) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                AuroraDesktopSidebar(
                    state, actions::add, TestProjectsSessionsComponent(),
                    Modifier.width(HbAuroraLayout.SidebarWithGap),
                )
            }
        }
        try {
            clock.frame(scene)
            val label = "Архивировать диалог «Сессия»"
            val status = "Новый результат"
            val before = scene.cell(status)
            assertNull(scene.description(label))
            scene.move(Offset(100f, before.center.y))
            val archive = scene.cell(label)
            assertEquals(32f, archive.width)
            assertEquals(28f, archive.height)
            assertTrue(archive.right < before.left)
            assertEquals(before, scene.cell(status))
            scene.capture("aurora-session-archive-hover")
            scene.move(Offset(HbAuroraLayout.NavigationWidth.value + 6f, before.center.y))
            assertNull(scene.description(label))
            scene.move(Offset(100f, before.center.y))
            scene.click(archive.center)
            assertEquals(listOf<AiStudioAction>(AiStudioAction.ArchiveChat(chat.id)), actions)
        } finally {
            scene.close()
        }
    }

    @Test
    fun moreChatsLeavesTheRestOfTheLineInactiveAndFocusFollowsSelection() {
        val more = NavigationRow("more:project", "Показать ещё", NavigationKind.More, "Проверки")
        val added = NavigationRow("entry", "Новая сессия", NavigationKind.Chat, "Проверки")
        val state = mutableStateOf(AiStudioScreenState(navigation = listOf(more)))
        val scale = mutableFloatStateOf(1f)
        val actions = mutableListOf<AiStudioAction>()
        val requestedProjects = mutableListOf<String>()
        val projectsSessions = object : TestProjectsSessionsComponent() {
            override fun loadMoreSessions(projectId: String) {
                requestedProjects.add(projectId)
                state.value = state.value.copy(navigation = listOf(added))
            }
        }
        val scene = ImageComposeScene(
            HbAuroraLayout.SidebarWithGap.value.toInt(), 200, coroutineContext = clock.context,
        ) {
            AuroraTestTheme(appearance = HbAppearance.Light, textScale = scale.floatValue) {
                AuroraDesktopSidebar(state.value, { action ->
                    actions.add(action)
                    state.value = when (action) {
                        AiStudioAction.OpenChat(added.id) ->
                            state.value.copy(navigation = listOf(added.copy(selected = true)))
                        else -> error("Unexpected action: $action")
                    }
                }, projectsSessions, Modifier.width(HbAuroraLayout.SidebarWithGap))
            }
        }
        try {
            scale.floatValue = 2f
            clock.frame(scene)
            assertTrue(scene.navigationRow(more.title).boundsInRoot.height >= 40f)
            scale.floatValue = 1f
            clock.frame(scene)
            val button = checkMoreButtonHitArea(scene, more.title)
            assertTrue(actions.isEmpty())
            assertTrue(requestedProjects.isEmpty())
            scene.click(button.center)
            assertEquals(listOf(more.project), requestedProjects)
            clock.frame(scene)
            val newRow = scene.navigationRow(added.title)
            assertTrue(newRow.config.getOrNull(SemanticsProperties.Focused) != true)
            scene.click(newRow.boundsInRoot.center)
            clock.frame(scene)
            val focused = scene.navigationRow(added.title)
            assertEquals(true, focused.config.getOrNull(SemanticsProperties.Focused))
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
            clock.frame(scene)
            assertEquals(
                listOf<AiStudioAction>(
                    AiStudioAction.OpenChat(added.id),
                    AiStudioAction.OpenChat(added.id),
                ),
                actions,
            )
        } finally {
            scene.close()
        }
    }

    private fun checkMoreButtonHitArea(scene: ImageComposeScene, title: String): Rect {
        val button = scene.navigationRow(title).boundsInRoot
        assertEquals(18f, button.left)
        assertEquals(HbAuroraLayout.NavigationRow.value, button.height)
        val inside = button.leftInset()
        val rest = scene.colorAt(inside)
        scene.move(inside)
        assertEquals(hoverColor, scene.colorAt(inside))
        val outside = Offset(230f, button.center.y)
        scene.move(outside)
        assertEquals(rest, scene.colorAt(inside))
        scene.click(outside)
        return button
    }

    private fun ImageComposeScene.navigationRow(title: String) = assertNotNull(node { row ->
        row.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == title }
    })

    @Test
    fun filesHeaderKeepsItsControlsOnOneRowAndFocusesSearch() {
        val state = mutableStateOf(AiStudioScreenState(filesVisible = true))
        val actions = mutableListOf<AiStudioAction>()
        val scene = ImageComposeScene(320, 240, coroutineContext = clock.context) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                AuroraFiles(state.value, touch = false, onAction = { action ->
                    actions.add(action)
                    if (action is AiStudioAction.ShowFileSearch) {
                        state.value = state.value.copy(fileSearchVisible = action.visible)
                    }
                })
            }
        }
        try {
            clock.frame(scene)
            val close = scene.cell("Свернуть панель файлов")
            val search = scene.cell("Поиск файлов")
            assertEquals(Rect(8f, 4f, 40f, 36f), close)
            assertEquals(Rect(280f, 4f, 312f, 36f), search)
            assertNull(scene.description("Поиск по файлам"))
            scene.move(search.leftInset())
            assertEquals(hoverColor, scene.colorAt(search.leftInset()))
            scene.click(search.center)
            val input = assertNotNull(scene.description("Поиск по файлам"))
            assertEquals(true, input.config.getOrNull(SemanticsProperties.Focused))
            scene.click(scene.cell("Закрыть поиск файлов").center)
            assertNull(scene.description("Поиск по файлам"))
            scene.click(close.center)
            assertEquals(
                listOf(
                    AiStudioAction.ShowFileSearch(true),
                    AiStudioAction.ShowFileSearch(false),
                    AiStudioAction.ToggleFiles(false),
                ),
                actions,
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun titleHoverAndMenuAreLimitedToTheTitleButton() {
        val scene = headerScene()
        try {
            clock.frame(scene)
            val title = scene.cell("Меню диалога «Единая модель сессий», проект Heartbeat")
            val left = title.leftInset()
            val right = title.rightInset()
            val emptySurface = Offset(600f, title.center.y)
            assertTrue(title.right < emptySurface.x)
            scene.move(emptySurface)
            assertEquals(HbAuroraColors.Pearl.toArgb(), scene.colorAt(emptySurface))
            scene.click(emptySurface)
            assertNull(scene.textNode("Файлы диалога"))
            scene.move(right)
            assertEquals(hoverColor, scene.colorAt(left))
            assertEquals(hoverColor, scene.colorAt(right))
            assertEquals(HbAuroraColors.Pearl.toArgb(), scene.colorAt(emptySurface))
            scene.capture("aurora-title-cell-hover")
            scene.click(right)
            assertNotNull(scene.textNode("Файлы диалога"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun clippedTitleShowsItsFullTextOnHoverAndKeyboardFocus() {
        val title = "Проверка восстановления положения чтения и длинного черновика после перезапуска приложения"
        val scene = headerScene(title = title, width = 360)
        val label = "Меню диалога «$title», проект Heartbeat"
        try {
            clock.frame(scene)
            val trigger = scene.cell(label)
            scene.move(trigger.rightInset())
            clock.reveal(scene)
            assertTrue(scene.hasTitleHint(title))
            scene.capture("aurora-title-tooltip")

            scene.move(Offset(2f, 150f))
            clock.advance(scene, 220)
            assertFalse(scene.hasTitleHint(title))

            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyUp))
            clock.frame(scene)
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyUp))
            clock.frame(scene)
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown, isShiftPressed = true))
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyUp, isShiftPressed = true))
            clock.frame(scene)
            clock.reveal(scene)
            assertEquals(true, assertNotNull(scene.description(label)).config.getOrNull(SemanticsProperties.Focused))
            assertTrue(scene.hasTitleHint(title))

            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
            clock.frame(scene)
            assertFalse(scene.hasTitleHint(title))
            assertNotNull(scene.node { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Файлы диалога" }
            })
        } finally {
            scene.close()
        }
    }

    @Test
    fun parameterHoverStaysInsideItsControlAndOutOfTheGaps() {
        val scene = headerScene()
        try {
            clock.frame(scene)
            val engine = scene.cell("Демонстрационный движок, Claude Code")
            val model = scene.cell("Демонстрационная модель, Sonnet 4.5")
            val effort = scene.cell("Уровень рассуждения, Средний")
            assertEquals(16f, model.left - engine.right)
            assertEquals(16f, effort.left - model.right)
            assertEquals(32f, effort.height)
            val gap = Offset((model.right + effort.left) / 2, effort.center.y)
            // Sample above the label and chevron, inside the control's background.
            val effortLeft = Offset(effort.left + 4f, effort.top + 4f)
            val effortRight = Offset(effort.right - 4f, effort.top + 4f)
            val modelRight = Offset(model.right - 4f, model.top + 4f)
            scene.move(effort.rightInset())
            assertEquals(hoverColor, scene.colorAt(effortLeft))
            assertEquals(hoverColor, scene.colorAt(effortRight))
            assertEquals(HbAuroraColors.Pearl.toArgb(), scene.colorAt(modelRight))
            scene.capture("aurora-effort-cell-hover")
            scene.move(model.leftInset())
            assertEquals(hoverColor, scene.colorAt(modelRight))
            assertEquals(HbAuroraColors.Pearl.toArgb(), scene.colorAt(effortLeft))
            scene.move(gap)
            assertEquals(HbAuroraColors.Pearl.toArgb(), scene.colorAt(gap))
            assertEquals(HbAuroraColors.Pearl.toArgb(), scene.colorAt(modelRight))
        } finally {
            scene.close()
        }
    }

    private fun headerScene(title: String = "Единая модель сессий", width: Int = 960) =
        ImageComposeScene(width, 160, coroutineContext = clock.context) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                HbDesktopFocusIndication(Modifier.fillMaxSize().background(HbAuroraColors.Canvas)) {
                    val chat = Chat("chat", title, "Heartbeat", effort = "Среднее")
                    AuroraHeader(AiStudioScreenState(chat = chat), touch = false, showNavigation = false, onAction = {})
                }
            }
        }

    private fun ImageComposeScene.cell(label: String) = assertNotNull(description(label)).boundsInRoot

    private fun Rect.leftInset() = Offset(left + 4f, center.y)

    private fun Rect.rightInset() = Offset(right - 4f, center.y)

    private fun ImageComposeScene.description(label: String) =
        node { label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    private fun ImageComposeScene.textNode(value: String) =
        node { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }

    private fun ImageComposeScene.node(matches: (SemanticsNode) -> Boolean) =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(matches) }

    private fun ImageComposeScene.hasTitleHint(title: String): Boolean = semanticsOwners.any { owner ->
        val root = owner.rootSemanticsNode
        root.find { node -> node.config.contains(SemanticsProperties.IsPopup) } != null &&
            root.find { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == title }
            } != null
    }

    private fun SemanticsNode.find(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (matches(this)) this else children.firstNotNullOfOrNull { it.find(matches) }

    private fun ImageComposeScene.move(point: Offset) {
        sendPointerEvent(PointerEventType.Move, point)
        clock.frame(this)
    }

    private fun ImageComposeScene.click(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        clock.frame(this)
    }

    private fun ImageComposeScene.colorAt(point: Offset) = render(clock.nanoTime).use { image ->
        Bitmap.makeFromImage(image).use { it.getColor(point.x.toInt(), point.y.toInt()) }
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
