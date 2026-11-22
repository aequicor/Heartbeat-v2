@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Quota
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Scenario
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Subagent
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHeader
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraHistoryEdges
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ContextAndQuotaDescription = "Контекст · использовано 61%, Лимиты за 5 ч · использовано 78%"

class AuroraHeaderTest {
    private val chat = Chat("chat", "Единая модель сессий", "Heartbeat", contextPercent = 61, quota = Quota(5, 78))
    private val initial = AiStudioScreenState(chat = chat, subagents = listOf(Subagent("agent", "Проверка контрактов")))

    @Test
    fun telemetryAndSubagentsOnlyUseSpaceForKnownData() {
        var state by mutableStateOf(initial)
        val scene = headerScene { state }
        try {
            scene.frame()
            val both = assertNotNull(scene.desc(ContextAndQuotaDescription)).boundsInRoot
            assertEquals(216f, both.width, absoluteTolerance = 1f)
            assertEquals(52f, both.top, absoluteTolerance = 1f)
            assertNotNull(scene.desc("Субагенты · 1"))

            state = state.copy(chat = chat.copy(quota = null), subagents = listOf())
            scene.frame()
            val onlyContext = assertNotNull(scene.desc("Контекст · использовано 61%"))
            assertTrue(onlyContext.boundsInRoot.width < both.width)
            assertEquals(both.right, onlyContext.boundsInRoot.right)
            assertNull(scene.desc("Субагенты · 1"))
            assertNull(scene.text("5 ч"))

            state = state.copy(chat = chat.copy(contextPercent = null))
            scene.frame()
            val onlyQuota = assertNotNull(scene.desc("Лимиты за 5 ч · использовано 78%")).boundsInRoot
            assertTrue(onlyQuota.width < both.width)
            assertEquals(both.right, onlyQuota.right)
            assertNull(scene.text("Контекст"))
            assertNull(scene.text("61%"))

            state = state.copy(chat = chat.copy(contextPercent = null, quota = null))
            scene.frame()
            assertNull(scene.text("Лимиты"))
            assertNull(scene.text("5 ч"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun narrowDesktopMetricsKeepBothValuesAvailableWithLargeText() {
        val scene = headerScene(width = 639, textScale = 2f) { initial }
        try {
            scene.frame()
            val metrics = assertNotNull(scene.desc(ContextAndQuotaDescription)).boundsInRoot
            assertTrue(metrics.width < 216f)
            assertNotNull(scene.text("61%"))
            assertNotNull(scene.text("5 ч"))
            scene.click(metrics.center)
            assertNotNull(scene.text("Демонстрационные данные"))
            assertNotNull(scene.text("Контекст · использовано 61%"))
            assertNotNull(scene.text("Лимиты за 5 ч · использовано 78%"))
            scene.press(Key.Escape)
            assertNull(scene.text("Демонстрационные данные"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun desktopMetricsWithTitleGrowForLargeText() {
        val scene = headerScene(width = 640, textScale = 2f) { initial }
        try {
            scene.frame()
            val metrics = assertNotNull(scene.desc(ContextAndQuotaDescription)).boundsInRoot
            assertTrue(metrics.width > 216f)
            assertNotNull(scene.text("Контекст"))
            assertNotNull(scene.text("61%"))
            assertNotNull(scene.text("5 ч"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun aMockScenarioAndASubagentCanBeOpenedFromTheDesktopHeader() {
        val received = mutableListOf<AiStudioAction>()
        val scene = headerScene(received::add) { initial }
        try {
            scene.frame()
            scene.click(assertNotNull(scene.desc("Демонстрационный движок, Claude Code")).boundsInRoot.center)
            assertNotNull(scene.text("Сценарий демонстрации"))
            scene.press(Key.Escape)
            assertNull(scene.text("Сценарий демонстрации"))
            scene.press(Key.Enter)
            scene.click(assertNotNull(scene.text("Ошибка отправки")).boundsInWindow.center)
            assertEquals(AiStudioAction.SetScenario(Scenario.SendFailure), received.single())

            scene.click(assertNotNull(scene.desc("Субагенты · 1")).boundsInRoot.center)
            scene.click(assertNotNull(scene.text("Проверка контрактов")).boundsInWindow.center)
            assertEquals(AiStudioAction.OpenChat("agent"), received.last())
        } finally {
            scene.close()
        }
    }

    @Test
    fun openingASubagentFocusesTheNewTitleAcrossTheChatTransition() {
        val child = chat.copy(id = "agent", title = "Проверка контрактов")
        var state by mutableStateOf(initial)
        val draft = MutableStateFlow(Draft())
        val liveText = MutableStateFlow(LiveText())
        val scene = ImageComposeScene(960, 540) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                    AuroraWorkspace(
                        state, draft, liveText, backdrop = null, touch = false, showNavigation = false,
                        onAction = { action ->
                            if (action == AiStudioAction.OpenChat(child.id)) {
                                state = state.copy(chat = child, subagents = listOf())
                            }
                        },
                    )
                }
            }
        }
        try {
            scene.frame()
            scene.click(assertNotNull(scene.desc("Субагенты · 1")).boundsInRoot.center)
            scene.press(Key.Enter)
            val title = assertNotNull(scene.desc("Меню диалога «${child.title}», проект ${child.project}"))
            assertEquals(true, title.config.getOrNull(SemanticsProperties.Focused))
            scene.press(Key.Enter)
            assertNotNull(scene.text("Файлы диалога"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun titleReceivesFocusIfTheFocusedCompactSubagentButtonDisappears() {
        var state by mutableStateOf(initial)
        val scene = headerScene(width = 639) { state }
        val titleLabel = "Меню диалога «${chat.title}», проект ${chat.project}"
        try {
            scene.frame()
            assertNull(scene.text("Субагенты · 1"))
            val titleBounds = assertNotNull(scene.desc(titleLabel)).boundsInRoot
            scene.press(Key.Tab)
            scene.press(Key.Tab)
            val subagents = assertNotNull(scene.desc("Субагенты · 1"))
            assertEquals(32f, subagents.boundsInRoot.width)
            assertEquals(true, subagents.config.getOrNull(SemanticsProperties.Focused))

            state = initial.copy(subagents = listOf())
            scene.frame()
            assertNull(scene.desc("Субагенты · 1"))
            val title = assertNotNull(scene.desc(titleLabel))
            assertEquals(titleBounds, title.boundsInRoot)
            assertEquals(true, title.config.getOrNull(SemanticsProperties.Focused))
        } finally {
            scene.close()
        }
    }

    @Test
    fun accessMenuKeepsTheCurrentModeSelectedAndSendsTheChosenMode() {
        var state by mutableStateOf(initial)
        val received = mutableListOf<AiStudioAction>()
        val scene = headerScene(received::add) { state }
        try {
            scene.frame()
            val trigger = assertNotNull(scene.desc("Режим доступа, ${Access.Workspace.label}"))
            assertEquals(HbAuroraLayout.HeaderButton.value, trigger.boundsInRoot.height)
            scene.click(trigger.boundsInRoot.center)
            val options = Access.entries.associateWith { mode ->
                assertNotNull(scene.node { node ->
                    node.config.getOrNull(SemanticsProperties.Selected) != null &&
                        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == mode.label }
                })
            }
            assertEquals(true, options.getValue(Access.Workspace).config.getOrNull(SemanticsProperties.Selected))
            assertEquals(false, options.getValue(Access.Auto).config.getOrNull(SemanticsProperties.Selected))
            assertTrue(options.getValue(Access.ReadOnly).boundsInWindow.top >= trigger.boundsInWindow.bottom)
            scene.click(options.getValue(Access.Auto).boundsInWindow.center)
            assertEquals(AiStudioAction.SetAccess(Access.Auto), received.single())
            assertNull(scene.text(Access.ReadOnly.label))

            state = initial.copy(chat = chat.copy(access = Access.Auto))
            scene.frame()
            assertNotNull(scene.desc("Режим доступа, ${Access.Auto.label}"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun touchHeaderKeepsKnownMetricsVisibleAndOpensExistingMenus() {
        val received = mutableListOf<AiStudioAction>()
        var state by mutableStateOf(initial)
        val scene = headerScene(received::add, touch = true) { state }
        try {
            scene.frame()
            val context = ContextAndQuotaDescription
            val metrics = assertNotNull(scene.desc(context)).boundsInRoot
            val percent = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                .single { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "61%" } }
                .boundsInRoot
            val contentWidth = percent.width + HbAuroraLayout.SmallGlyph.value + HbAuroraLayout.ControlGap.value +
                2 * HbAuroraLayout.MessageRadius.value
            assertEquals(maxOf(HbAuroraLayout.TouchHeaderMetricWidth.value, contentWidth), metrics.width, 1f)
            assertEquals(48f, metrics.height, absoluteTolerance = 1f)
            scene.click(assertNotNull(scene.desc("Субагенты · 1")).boundsInRoot.center)
            scene.click(assertNotNull(scene.text("Проверка контрактов")).boundsInWindow.center)
            assertEquals(AiStudioAction.OpenChat("agent"), received.last())

            state = state.copy(chat = chat.copy(contextPercent = null), subagents = listOf())
            scene.frame()
            assertNull(scene.desc("Субагенты · 1"))
            assertNotNull(scene.desc("Лимиты за 5 ч · использовано 78%"))
            scene.click(assertNotNull(scene.desc("Единая модель сессий, Heartbeat")).boundsInRoot.center)
            scene.click(assertNotNull(scene.text("Файлы диалога")).boundsInWindow.center)
            assertEquals(AiStudioAction.ToggleFiles(true), received.last())
        } finally {
            scene.close()
        }
    }

    @Test
    fun desktopParametersKeepInnerInsetsWhenMetricsUseCompactLayout() {
        val inset = HbAuroraLayout.ContentInset
        val scene = headerScene(width = 656, edgeInset = inset) { initial }
        try {
            scene.frame()
            val engine = assertNotNull(scene.desc("Демонстрационный движок, Claude Code")).boundsInRoot
            val effort = assertNotNull(scene.desc("Уровень рассуждения, Средний")).boundsInRoot
            val access = assertNotNull(scene.desc("Режим доступа, ${Access.Workspace.label}")).boundsInRoot
            assertNull(scene.text("Контекст"))
            assertEquals(inset.value * 2, engine.left)
            assertEquals(engine.top, effort.top)
            if (access.top == effort.top) {
                assertEquals(inset.value + HbAuroraLayout.HeaderClusterGap.value, access.left - effort.right)
            } else {
                assertTrue(access.top >= effort.bottom)
                assertEquals(inset.value, access.left, "A wrapped parameter keeps the header inset")
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun desktopParametersWrapWithinTheHeaderInsets() {
        val access = "Режим доступа, ${Access.Workspace.label}"
        val labels = listOf(
            "Демонстрационный движок, Claude Code", "Демонстрационная модель, Sonnet 4.5",
            "Уровень рассуждения, Средний", access,
        )
        val received = mutableListOf<AiStudioAction>()
        val withoutMetrics = initial.copy(
            chat = chat.copy(contextPercent = null, quota = null), subagents = listOf(),
        )
        var state by mutableStateOf(withoutMetrics)
        val width = 328
        val inset = HbAuroraLayout.ContentInset
        val scene = headerScene(received::add, width = width, edgeInset = inset) { state }
        val wide = headerScene { withoutMetrics }
        try {
            scene.frame()
            wide.frame()
            val bounds = labels.map { label ->
                assertNotNull(scene.desc(label)).boundsInRoot.also {
                    assertEquals(assertNotNull(wide.desc(label)).boundsInRoot.width, it.width)
                }
            }
            assertTrue(bounds.all { it.left >= inset.value && it.right <= width - inset.value })
            assertEquals(inset.value * 2, bounds.first().left)
            assertEquals(bounds.first().top, bounds[1].top)
            assertEquals(inset.value * 2, bounds[2].left)
            assertTrue(bounds[2].top >= bounds.first().bottom)
            assertEquals(inset.value, bounds.last().left)
            assertTrue(bounds.last().top > bounds[2].bottom)

            repeat(5) { scene.press(Key.Tab) }
            assertEquals(true, assertNotNull(scene.desc(access)).config.getOrNull(SemanticsProperties.Focused))
            scene.press(Key.Enter)
            scene.click(assertNotNull(scene.text(Access.Auto.label)).boundsInWindow.center)
            assertEquals(AiStudioAction.SetAccess(Access.Auto), received.single())

            state = withoutMetrics.copy(chat = chat)
            scene.frame()
            val metrics = assertNotNull(scene.desc(ContextAndQuotaDescription)).boundsInRoot
            assertTrue(metrics.top > bounds.last().bottom)
            assertTrue(metrics.left >= inset.value && metrics.right <= width - inset.value)
            assertEquals(bounds, labels.map { assertNotNull(scene.desc(it)).boundsInRoot })
        } finally {
            scene.close()
            wide.close()
        }
    }

    @Test
    fun touchParametersScrollUpToTheEdgesWithKnownMetricsPinned() {
        val clock = AuroraHoverTestClock()
        var state by mutableStateOf(initial)
        val scene = ImageComposeScene(320, 540, coroutineContext = clock.context) {
            AuroraTestTheme(density = HbDensity.Touch) {
                AuroraHeader(state, touch = true, showNavigation = true, {}, edgeInset = 12.dp)
            }
        }
        try {
            clock.frame(scene)
            fun strip() = assertNotNull(scene.node {
                it.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
            })
            val metrics = assertNotNull(scene.desc(ContextAndQuotaDescription))
            assertEquals(12f, assertNotNull(scene.desc("Диалоги")).boundsInRoot.left)
            assertEquals(0f, strip().boundsInRoot.left)
            assertEquals(308f, metrics.boundsInRoot.right)
            assertEquals(metrics.boundsInRoot.left - 8f, strip().boundsInRoot.right)

            state = initial.copy(chat = chat.copy(contextPercent = null, quota = null))
            clock.frame(scene)
            val viewport = strip()
            assertEquals(0f, viewport.boundsInRoot.left)
            assertEquals(320f, viewport.boundsInRoot.right)
            val scroll = assertNotNull(viewport.config.getOrNull(SemanticsActions.ScrollBy)?.action)
            assertTrue(assertNotNull(scene.desc("Режим доступа, ${Access.Workspace.label}")).boundsInRoot.width > 0f)
            assertTrue(scroll(-640f, 0f))
            repeat(24) { clock.advance(scene, 16) }
            val engine = assertNotNull(scene.desc("Демонстрационный движок, Claude Code"))
            assertEquals(0f, engine.boundsInRoot.left, 1f)
            assertTrue(assertNotNull(engine.config.getOrNull(SemanticsActions.OnClick)?.action)())
            clock.frame(scene)
            assertNotNull(scene.text("Сценарий демонстрации"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun onlyTheHistoryUnderTheFloatingControlsIsBlurred() {
        val scene = ImageComposeScene(420, 210) {
            Canvas(Modifier.fillMaxSize().auroraHistoryEdges(88, bottomHeightPx = 64, readingWidth = 240.dp)) {
                drawRect(Color.Black)
                drawRect(Color.White, Offset(210f, 0f), Size(210f, size.height))
            }
        }
        try {
            scene.render().use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    assertEquals(Color.Black.toArgb(), bitmap.getColor(209, 110))
                    assertEquals(Color.White.toArgb(), bitmap.getColor(210, 110))
                    assertTrue(bitmap.getColor(209, 30) != Color.Black.toArgb())
                    assertTrue(bitmap.getColor(210, 30) != Color.White.toArgb())
                    assertEquals(Color.White.toArgb(), bitmap.getColor(210, 145))
                    assertTrue(bitmap.getColor(210, 190) != Color.White.toArgb())
                }
            }
        } finally {
            scene.close()
        }
    }

    private fun headerScene(
        onAction: (AiStudioAction) -> Unit = {},
        touch: Boolean = false,
        width: Int = if (touch) 390 else 960,
        textScale: Float = 1f,
        edgeInset: Dp = Dp.Hairline,
        state: () -> AiStudioScreenState,
    ) = ImageComposeScene(width, 540) {
        AuroraTestTheme(textScale = textScale, density = if (touch) HbDensity.Touch else HbDensity.Dense) {
            HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                AuroraHeader(state(), touch = touch, showNavigation = touch, onAction, edgeInset = edgeInset)
            }
        }
    }

    private fun ImageComposeScene.desc(label: String) =
        node { label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    private fun ImageComposeScene.text(label: String) =
        node { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } }

    private fun ImageComposeScene.node(where: (SemanticsNode) -> Boolean): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(where) }

    private fun SemanticsNode.find(where: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (where(this)) this else children.firstNotNullOfOrNull { it.find(where) }

    private fun ImageComposeScene.click(point: Offset) {
        sendPointerEvent(PointerEventType.Move, point)
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        frame()
    }

    private fun ImageComposeScene.press(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        frame()
    }

    private fun ImageComposeScene.frame() = repeat(3) { render().close() }
}
