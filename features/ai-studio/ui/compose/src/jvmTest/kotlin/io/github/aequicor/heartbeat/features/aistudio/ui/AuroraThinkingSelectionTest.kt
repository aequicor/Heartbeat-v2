@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkEvent
import org.jetbrains.skia.EncodedImageFormat
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuroraThinkingSelectionTest {
    @Test
    fun desktopDragAndCopyStayOnTheDisplayedRevisionUntilSelectionEnds() {
        val beforeBoundary = "Строка перед стыком."
        val afterBoundary = "Строка после стыка."
        val selected = "$beforeBoundary\n$afterBoundary"
        val row = "Длинное рассуждение остаётся частью хода работы.\n"
        val initial = row.repeat(31) + "$selected\n" + row.repeat(64)
        val newChunk = "Продолжаю проверку."
        val lastChunk = "Последний фрагмент принят."
        val appended = initial + newChunk
        val final = "$appended\n$lastChunk"
        var event by mutableStateOf(thinking(initial))
        val clock = AuroraHoverTestClock()
        val clipboard = RecordingClipboard()
        val scene = ImageComposeScene(640, 480, coroutineContext = clock.context) {
            AuroraTestTheme(motion = HbMotionPreference.Reduced) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    Box(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                        AuroraWorkEvent(event, expanded = true, current = true, connectedBelow = false, touch = false) {
                            error("Selecting details must not toggle the thinking header")
                        }
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            scene.scrollDetails(clock, 550f)
            val before = assertNotNull(scene.textNode(beforeBoundary))
            val after = assertNotNull(scene.textNode(afterBoundary))
            assertNotEquals(before.id, after.id, "The mouse selection must cross two different text nodes")
            val start = before.textCursor(beforeBoundary, 0)
            val end = after.textCursor(afterBoundary, afterBoundary.length)
            scene.sendPointerEvent(PointerEventType.Move, start)
            scene.sendPointerEvent(PointerEventType.Press, start, button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Move, end, buttons = PointerButtons(isPrimaryPressed = true))
            clock.frame(scene)
            event = thinking(appended)
            clock.frame(scene)
            scene.sendPointerEvent(PointerEventType.Release, end, button = PointerButton.Primary)
            clock.frame(scene)

            scene.copySelection()
            clock.frame(scene)
            assertEquals(selected, clipboard.text)
            scene.capture("aurora-thinking-selected", clock.nanoTime)

            event = thinking(final)
            clock.frame(scene)
            scene.copySelection()
            clock.frame(scene)
            assertEquals(listOf(selected, selected), clipboard.writes)

            scene.clickAt(Offset(end.x + 16f, end.y + 25f))
            clock.frame(scene)
            scene.scrollDetails(clock, 5_000f)
            assertNotNull(scene.textNode(newChunk))
            assertNotNull(scene.textNode(lastChunk))
            scene.capture("aurora-thinking-released-tail", clock.nanoTime)
        } finally {
            scene.close()
        }
    }

    @Test
    fun markdownThinkingKeepsTheSelectedWordsAndPublishesAnIndependentTail() = runBlocking<Unit> {
        val clock = AuroraHoverTestClock()
        val clipboard = RecordingClipboard()
        val first = "Review **stable words** here."
        var event by mutableStateOf(thinking(first).copy(detailsFormat = DetailFormat.Markdown))
        val scene = ImageComposeScene(640, 480, coroutineContext = clock.context) {
            AuroraTestTheme(motion = HbMotionPreference.Reduced) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    AuroraWorkEvent(event, expanded = true, current = true, connectedBelow = false, touch = false) {}
                }
            }
        }
        try {
            val firstLine = scene.awaitText(clock, "Review stable words here.")
            val spans = firstLine.config[SemanticsProperties.Text].single().spanStyles
            assertTrue(spans.any { it.item.fontWeight == FontWeight.SemiBold })
            val start = firstLine.textCursor("stable words", 0)
            val end = firstLine.textCursor("stable words", "stable words".length)
            scene.sendPointerEvent(PointerEventType.Move, start)
            scene.sendPointerEvent(PointerEventType.Press, start, button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Move, end, buttons = PointerButtons(isPrimaryPressed = true))
            clock.frame(scene)
            event = event.copy(details = "$first\n\n## Next step")
            val tail = scene.awaitText(clock, "Next step") { node ->
                node.config.getOrNull(SemanticsProperties.Heading) != null
            }
            assertTrue(tail.layoutInfo.isPlaced)
            scene.sendPointerEvent(PointerEventType.Release, end, button = PointerButton.Primary)
            scene.copySelection()
            clock.frame(scene)
            assertEquals("stable words", clipboard.text)
            event = event.copy(details = "$first\n\n## Next step\n\nFinal line.", status = StepStatus.Done)
            scene.awaitText(clock, "Final line")
            scene.copySelection()
            clock.frame(scene)
            assertEquals(listOf("stable words", "stable words"), clipboard.writes)
        } finally {
            scene.close()
        }
    }

    private suspend fun ImageComposeScene.awaitText(
        clock: AuroraHoverTestClock,
        text: String,
        match: (SemanticsNode) -> Boolean = { true },
    ): SemanticsNode = withTimeout(5_000) {
        var found = textNode(text)?.takeIf(match)
        while (found == null) {
            clock.advance(this@awaitText, 16)
            delay(1)
            found = textNode(text)?.takeIf(match)
        }
        found
    }

    private fun ImageComposeScene.clickAt(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
    }

    private fun thinking(text: String) = WorkEvent(
        "reasoning", "Думает...", text, text, WorkEventKind.Thinking, StepStatus.Current,
    )

    private fun SemanticsNode.textCursor(marker: String, offset: Int): Offset {
        val text = config[SemanticsProperties.Text].single().text
        val getLayout = assertNotNull(config[SemanticsActions.GetTextLayoutResult].action)
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(getLayout(layouts))
        val cursor = layouts.single().getCursorRect(text.indexOf(marker) + offset)
        return positionInRoot + cursor.center + Offset(.5f, 0f)
    }

    private fun ImageComposeScene.textNode(text: String): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.findText(text) }

    private fun SemanticsNode.findText(text: String): SemanticsNode? {
        if (config.getOrNull(SemanticsProperties.Text).orEmpty().any { text in it.text }) return this
        return children.firstNotNullOfOrNull { it.findText(text) }
    }

    private fun ImageComposeScene.scrollDetails(clock: AuroraHoverTestClock, delta: Float) {
        val root = semanticsOwners.first().rootSemanticsNode
        val scroll = assertNotNull(root.scrollAction())
        assertTrue(scroll(0f, delta))
        repeat(24) { clock.advance(this, 16) }
    }

    private fun SemanticsNode.scrollAction(): ((Float, Float) -> Boolean)? =
        config.getOrNull(SemanticsActions.ScrollBy)?.action ?: children.firstNotNullOfOrNull { it.scrollAction() }

    private fun ImageComposeScene.copySelection() {
        val mac = System.getProperty("os.name").startsWith("Mac")
        sendKeyEvent(KeyEvent(Key.C, KeyEventType.KeyDown, isCtrlPressed = !mac, isMetaPressed = mac))
    }

    private fun ImageComposeScene.capture(name: String, nanoTime: Long) {
        val directory = File("build/screenshots").apply { mkdirs() }
        render(nanoTime).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { File(directory, "$name.png").writeBytes(it.bytes) }
        }
    }

    private class RecordingClipboard : Clipboard {
        private var entry: ClipEntry? = null
        val writes = mutableListOf<String>()
        val text: String? get() = writes.lastOrNull()

        override suspend fun getClipEntry(): ClipEntry? = entry

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
            val value = clipEntry?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String
            if (value != null) writes += value
        }
    }
}
