@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.font.FontWeight
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraReplyLayout
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraViewer
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkEvent
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuroraMarkdownSurfacesTest {
    @Test
    fun fileExtensionEnablesMarkdownInTheViewer() = runBlocking<Unit> {
        val clock = AuroraHoverTestClock()
        val file = File("file", "architecture.MD", "Текст", "# Architecture\n\nA **stable** boundary.")
        val scene = ImageComposeScene(620, 420, coroutineContext = clock.context) {
            AuroraTestTheme(motion = HbMotionPreference.Reduced) {
                AuroraViewer(file, touch = false) {}
            }
        }
        try {
            assertNotNull(scene.awaitText("Architecture", clock).config.getOrNull(SemanticsProperties.Heading))
            val styled = scene.awaitText("A stable boundary.", clock).config[SemanticsProperties.Text].single()
            assertTrue(styled.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
            assertNull(scene.textNode(file.content))
        } finally {
            scene.close()
        }
    }

    @Test
    fun toolOutputUsesItsDeclaredFormatAndKeepsTheFileLabel() = runBlocking<Unit> {
        val clock = AuroraHoverTestClock()
        val markdown = WorkEvent(
            "notes", "Read notes", "Done", "## Tool notes\n\nA `command` output.",
            WorkEventKind.File, StepStatus.Done, "Файл: notes.md", DetailFormat.Markdown,
        )
        val source = "# Warning\n\n**exit**: 1"
        val plain = markdown.copy(id = "log", title = "Tool log", details = source, detailsFormat = DetailFormat.Plain)
        val scene = ImageComposeScene(620, 420, coroutineContext = clock.context) {
            AuroraTestTheme(motion = HbMotionPreference.Reduced) {
                Column {
                    for (event in listOf(markdown, plain)) {
                        AuroraWorkEvent(
                            event, expanded = true, current = false, connectedBelow = false, touch = false,
                        ) {}
                    }
                }
            }
        }
        try {
            assertNotNull(scene.awaitText("Tool notes", clock).config.getOrNull(SemanticsProperties.Heading))
            assertNotNull(scene.textNode("Файл: notes.md"))
            assertEquals(source, assertNotNull(scene.textNode(source)).config[SemanticsProperties.Text].single().text)
            assertNull(scene.textNode("Warning"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun largeToolMarkdownWithEmbeddedViewportsFitsTheReplyWidth() = runBlocking<Unit> {
        val clock = AuroraHoverTestClock()
        val fence = "\n\n```kotlin\nval counter = 10\n```"
        val markdown = WorkEvent(
            "notes", "Read notes", "Done", "## Tool notes" + fence.repeat(6),
            WorkEventKind.File, StepStatus.Done, "Файл: notes.md", DetailFormat.Markdown,
        )
        val scene = ImageComposeScene(620, 420, coroutineContext = clock.context) {
            AuroraTestTheme(motion = HbMotionPreference.Reduced) {
                AuroraReplyLayout(
                    history = {
                        AuroraWorkEvent(
                            markdown, expanded = true, current = false, connectedBelow = false, touch = false,
                        ) {}
                    },
                    answer = { BasicText("A short answer.") },
                    modifier = Modifier.verticalScroll(rememberScrollState()).testTag("markdown-reply"),
                    expandToFitHistory = true,
                )
            }
        }
        try {
            clock.frame(scene)
            val before = assertNotNull(scene.taggedNode("markdown-reply")).boundsInRoot.width
            val heading = scene.awaitText("Tool notes", clock)
            assertNotNull(heading.config.getOrNull(SemanticsProperties.Heading))
            assertNotNull(scene.awaitText("val counter = 10", clock))
            assertEquals(before, assertNotNull(scene.taggedNode("markdown-reply")).boundsInRoot.width)
        } finally {
            scene.close()
        }
    }

    @Test
    fun interimAndStageResultsUseTheSharedMarkdownRenderer() = runBlocking<Unit> {
        val source = "## Результат этапа\n\nУсловие **выполнено**.\n\n" +
            "- Первый пункт\n- Второй пункт\n\n| Поле | Значение |\n| --- | --- |\n| Якорь | Сохранён |\n\n" +
            "```kotlin\nval anchor = 1\n```"
        for (touch in listOf(false, true)) for (asInterim in listOf(false, true)) {
            val clock = AuroraHoverTestClock()
            val step = Step("scan", "Проверка", "", StepStatus.Done, result = source.takeUnless { asInterim })
            val reply = Reply("reply", "", steps = listOf(step), interim = source.takeIf { asInterim })
            val scene = ImageComposeScene(620, 720, coroutineContext = clock.context) {
                AuroraTestTheme(motion = HbMotionPreference.Reduced) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AuroraReplyLayout(
                            history = { AuroraWorkTimeline(reply, setOf(), setOf(), touch, {}) },
                            answer = { BasicText("Короткий итог") },
                            expandToFitHistory = true,
                        )
                    }
                }
            }
            try {
                assertNotNull(scene.awaitText("Результат этапа", clock).config.getOrNull(SemanticsProperties.Heading))
                val paragraph = scene.awaitText("Условие выполнено.", clock).config[SemanticsProperties.Text].single()
                assertTrue(paragraph.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
                assertNotNull(scene.awaitText("Якорь", clock))
                assertNotNull(scene.awaitText("val anchor = 1", clock))
                assertNull(scene.textNode(source))
            } finally { scene.close() }
        }
    }
    private suspend fun ImageComposeScene.awaitText(text: String, clock: AuroraHoverTestClock): SemanticsNode =
        withTimeout(5_000) {
            var found = textNode(text)
            while (found == null) {
                clock.advance(this@awaitText, 16)
                delay(1)
                found = textNode(text)
            }
            found
        }

    private fun ImageComposeScene.textNode(text: String): SemanticsNode? = firstVisibleNode { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
    }

    private fun ImageComposeScene.taggedNode(tag: String): SemanticsNode? = firstVisibleNode { node ->
        node.config.getOrNull(SemanticsProperties.TestTag) == tag
    }

    private fun ImageComposeScene.firstVisibleNode(match: (SemanticsNode) -> Boolean): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { owner ->
            owner.getAllSemanticsNodes(mergingEnabled = false).firstOrNull { node ->
                node.layoutInfo.isPlaced && !node.boundsInRoot.isEmpty && match(node)
            }
        }
}
