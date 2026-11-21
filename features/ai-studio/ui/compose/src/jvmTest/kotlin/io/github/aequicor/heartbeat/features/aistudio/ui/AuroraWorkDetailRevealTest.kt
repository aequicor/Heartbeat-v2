@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkDetailContent
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraThinkingPreview
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.Closeable
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuroraWorkDetailRevealTest {
    @Test
    fun eachDetailFormatCascadesWithoutFadingOldTextOrDelayingLayout() = runBlocking<Unit> {
        for (format in DetailFormat.entries) for (kind in listOf(WorkEventKind.File, WorkEventKind.Thinking)) {
            DetailRevealScene(format, kind, source = "").use { fixture ->
                fixture.clock.frame(fixture.scene)
                val first = if (format == DetailFormat.Markdown) "**AAAA**" else "AAAA"
                fixture.event = fixture.event.copy(details = first)
                fixture.awaitWord("AAAA")
                val initial = fixture.wordInk("AAAA")
                assertTrue(initial > 0)
                fixture.clock.advance(fixture.scene, 200)
                val old = fixture.wordInk("AAAA")
                assertTrue(old > initial)
                val tail = if (format == DetailFormat.Markdown) "**BBBB CCCC DDDD**" else "BBBB CCCC DDDD"
                fixture.event = fixture.event.copy(details = fixture.event.details + "\n\n" + tail)
                fixture.awaitWord("BBBB")
                val height = fixture.height
                assertEquals(old.toDouble(), fixture.wordInk("AAAA").toDouble(), old / 1000.0)
                assertTrue(fixture.wordInk("BBBB") > 0)
                assertEquals(0L, fixture.wordInk("CCCC"))
                assertEquals(0L, fixture.wordInk("DDDD"))
                fixture.clock.advance(fixture.scene, 24)
                assertEquals(height, fixture.height)
                assertTrue(fixture.wordInk("CCCC") > 0)
                assertEquals(0L, fixture.wordInk("DDDD"))
                fixture.event = fixture.event.copy(
                    details = fixture.event.details + "\n\nEEEE", status = StepStatus.Done,
                )
                fixture.awaitWord("EEEE")
                val final = fixture.image().ink()
                fixture.clock.advance(fixture.scene, 300)
                assertEquals(final, fixture.image().ink(), "Completion must not wait for the reveal queue")
            }
        }
    }

    @Test
    fun largeBurstsDoNotLeaveAnEmptyViewport() = runBlocking<Unit> {
        val source = List(24) { "AAAA BBBB CCCC DDDD EEEE FFFF GGGG HHHH" }.joinToString("\n\n")
        for (format in DetailFormat.entries) {
            DetailRevealScene(format, WorkEventKind.Thinking, source, atBottom = true).use { fixture ->
                fixture.awaitWord("HHHH")
                assertTrue(fixture.height > 320)
                val first = fixture.image().ink(Rect(0f, 0f, 600f, 220f))
                assertTrue(first > 0, "Received details must not reserve an invisible viewport")
                fixture.event = fixture.event.copy(status = StepStatus.Done)
                fixture.awaitWord("HHHH")
                assertEquals(first, fixture.image().ink(Rect(0f, 0f, 600f, 220f)))
            }
        }
    }

    @Test
    fun completionFlushesSelectedAndUnclosedTailsWithoutAdvancingTheAnimationClock() = runBlocking<Unit> {
        val source = "AAAA\n\nBBBB CCCC [unfinished](htt"
        for (format in DetailFormat.entries) for (status in listOf(
            StepStatus.Done, StepStatus.Stopped, StepStatus.Failed,
        )) {
            DetailRevealScene(format, WorkEventKind.Thinking).use { fixture ->
                fixture.awaitWord("AAAA")
                fixture.selection.selectAll()
                fixture.clock.frame(fixture.scene)
                fixture.clock.advance(fixture.scene, 5)
                val final = if (format == DetailFormat.Markdown) source.replace("AAAA", "**AAAA**") else source
                fixture.event = fixture.event.copy(details = final, status = status)
                fixture.awaitWord("unfinished")
                fixture.selection.clear()
                fixture.clock.frame(fixture.scene)
                val finalInk = fixture.image().ink()
                DetailRevealScene(format, WorkEventKind.Thinking, final, status = status).use { reference ->
                    reference.awaitWord("unfinished")
                    assertEquals(reference.image().ink(), finalInk, "Final details use the ordinary complete renderer")
                }
                fixture.clock.advance(fixture.scene, 300)
                assertEquals(finalInk, fixture.image().ink())
            }
        }
    }

    @Test
    fun reducedMotionShowsEveryDetailFormatImmediatelyOnDesktopAndTouch() = runBlocking<Unit> {
        for (format in DetailFormat.entries) for (touch in listOf(false, true)) {
            DetailRevealScene(format, WorkEventKind.Thinking, "AAAA BBBB CCCC", touch = touch,
                motion = HbMotionPreference.Reduced).use { fixture ->
                fixture.awaitWord("CCCC")
                val first = fixture.image().ink()
                fixture.event = fixture.event.copy(status = StepStatus.Done)
                fixture.awaitWord("CCCC")
                assertEquals(first, fixture.image().ink())
            }
        }
    }

    @Test
    fun aNewEventHasAnIndependentReveal() = runBlocking<Unit> {
        DetailRevealScene(DetailFormat.Plain, WorkEventKind.File).use { fixture ->
            fixture.awaitWord("AAAA")
            fixture.clock.advance(fixture.scene, 200)
            val old = fixture.wordInk("AAAA")
            fixture.event = fixture.event.copy(id = "next-event", details = "AAAA BBBB CCCC")
            fixture.awaitWord("CCCC")
            assertTrue(fixture.wordInk("AAAA") < old)
            assertEquals(0L, fixture.wordInk("CCCC"))
        }
    }

    @Test
    fun collapsedThinkingCascadesAndRetainsItsTwoLinePreview() = runBlocking<Unit> {
        DetailRevealScene(DetailFormat.Plain, WorkEventKind.Thinking, preview = true).use { fixture ->
            fixture.awaitWord("AAAA")
            fixture.clock.advance(fixture.scene, 200)
            val old = fixture.wordInk("AAAA")
            fixture.event = fixture.event.copy(summary = "AAAA BBBB CCCC")
            fixture.awaitWord("CCCC")
            // Caption edge antialiasing shifts slightly when its offscreen layer grows.
            assertEquals(old.toDouble(), fixture.wordInk("AAAA").toDouble(), old * 0.005)
            assertTrue(fixture.wordInk("BBBB") > 0)
            assertEquals(0L, fixture.wordInk("CCCC"))
            fixture.event = fixture.event.copy(
                summary = "AAAA BBBB CCCC " + "tail ".repeat(300), status = StepStatus.Done,
            )
            fixture.awaitWord("tail")
            val layout = fixture.textLayout("AAAA")
            assertEquals(2, layout.lineCount)
            assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
            assertTrue(layout.getLineEnd(1) < layout.layoutInput.text.length)
            val final = fixture.image().ink()
            assertEquals(nativePreviewInk(fixture.event.preview), final)
            fixture.clock.advance(fixture.scene, 300)
            assertEquals(final, fixture.image().ink())
        }
    }
    @Test
    fun thinkingTrailingBlankLinesKeepTheSourceWithoutReservingEmptyHeight() = runBlocking<Unit> {
        val sources = listOf("Начало\n\nEND", "ROW\n".repeat(31) + "END")
        for (touch in listOf(false, true)) for (source in sources) {
            DetailRevealScene(DetailFormat.Plain, WorkEventKind.Thinking, source, touch = touch).use { fixture ->
                fixture.awaitWord("END")
                val height = fixture.height
                for (ending in listOf("\n\n", "\r\n\r\n", " \n \n\t")) {
                    fixture.event = fixture.event.copy(details = source + ending, status = StepStatus.Current)
                    fixture.clock.frame(fixture.scene)
                    assertEquals(height, fixture.height, "Trailing blank lines must not enlarge thinking")
                    fixture.selection.selectAll()
                    fixture.clock.frame(fixture.scene)
                    assertEquals(source + ending, fixture.selection.getSelectableTexts().joinToString("\n"))
                    fixture.selection.clear()
                    fixture.clock.frame(fixture.scene)
                    fixture.event = fixture.event.copy(details = source + ending + "NEXT")
                    fixture.awaitWord("NEXT")
                    assertTrue(fixture.height > height, "Paragraph spacing must remain inside the text")
                    fixture.event = fixture.event.copy(details = source + ending, status = StepStatus.Done)
                    fixture.clock.frame(fixture.scene)
                    assertEquals(height, fixture.height, "Completion must not restore the empty tail")
                }
            }
        }
    }

    @Test
    fun thinkingBlockRolloverDoesNotRevealPreviouslyVisibleTextAgain() = runBlocking<Unit> {
        val source = "AAAA\n".repeat(31) + "OLD"
        DetailRevealScene(DetailFormat.Plain, WorkEventKind.Thinking, source, atBottom = true).use { fixture ->
            fixture.awaitWord("OLD")
            fixture.clock.advance(fixture.scene, 200)
            val before = fixture.wordInk("OLD")
            fixture.event = fixture.event.copy(details = source + "\nNEW")
            fixture.awaitWord("NEW")
            assertEquals(before, fixture.wordInk("OLD"))
        }
    }
}

private class DetailRevealScene(
    format: DetailFormat,
    kind: WorkEventKind,
    source: String = "AAAA",
    status: StepStatus = StepStatus.Current,
    touch: Boolean = false,
    atBottom: Boolean = false,
    preview: Boolean = false,
    motion: HbMotionPreference = HbMotionPreference.Full,
) : Closeable {
    val clock = AuroraHoverTestClock()
    var event by mutableStateOf(WorkEvent("event", "Tool", source, source, kind, status, detailsFormat = format))
    lateinit var selection: SelectionState
    var height = 0
    val scene = ImageComposeScene(600, 320, coroutineContext = clock.context) {
        AuroraTestTheme(motion = motion) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Column(Modifier.verticalScroll(rememberScrollState(if (atBottom) Int.MAX_VALUE else 0))) {
                    Column(Modifier.onSizeChanged { height = it.height }) {
                        selection = rememberSelectionState()
                        SelectionContainer(state = selection) {
                            Column {
                                if (preview) AuroraThinkingPreview(event, HbAuroraColors.DesktopWorkMuted)
                                else AuroraWorkDetailContent(event, touch, selection)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun awaitWord(word: String) = withTimeout(5_000) {
        do {
            clock.frame(scene)
            val node = wordNode(word)
            if (node == null) delay(1)
        } while (node == null)
    }

    private fun wordNode(word: String): SemanticsNode? = scene.semanticsOwners.firstNotNullOfOrNull { owner ->
        owner.getAllSemanticsNodes(mergingEnabled = false).firstOrNull { node ->
            node.layoutInfo.isPlaced && node.config.getOrNull(SemanticsProperties.Text).orEmpty()
                .any { word in it.text }
        }
    }

    fun wordInk(word: String): Long {
        val node = assertNotNull(wordNode(word))
        val text = node.config[SemanticsProperties.Text].single().text
        val start = text.indexOf(word)
        val bounds = textLayout(word).getPathForRange(start, start + word.length).getBounds()
        return image().ink(bounds.translate(node.positionInRoot))
    }

    fun textLayout(word: String): TextLayoutResult {
        val node = assertNotNull(wordNode(word))
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(assertNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)(layouts))
        return layouts.single()
    }
    fun image(): BufferedImage = scene.render(clock.nanoTime).use { frame ->
        val bytes = checkNotNull(frame.encodeToData()).use { it.bytes }
        ImageIO.read(ByteArrayInputStream(bytes))
    }

    override fun close() = scene.close()
}

private fun nativePreviewInk(text: String): Long {
    val clock = AuroraHoverTestClock()
    val scene = ImageComposeScene(600, 320, coroutineContext = clock.context) {
        AuroraTestTheme {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BasicText(text, style = HbTypography.Caption.copy(color = HbAuroraColors.DesktopWorkMuted),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    try {
        clock.frame(scene)
        return scene.render(clock.nanoTime).use { frame ->
            val bytes = checkNotNull(frame.encodeToData()).use { it.bytes }
            ImageIO.read(ByteArrayInputStream(bytes)).ink()
        }
    } finally { scene.close() }
}
private fun BufferedImage.ink(bounds: Rect = Rect(0f, 0f, width.toFloat(), height.toFloat())): Long {
    var ink = 0L
    for (y in floor(bounds.top).toInt().coerceAtLeast(0) until ceil(bounds.bottom).toInt().coerceAtMost(height)) {
        for (x in floor(bounds.left).toInt().coerceAtLeast(0) until ceil(bounds.right).toInt().coerceAtMost(width)) {
            ink += 255 - (getRGB(x, y) and 255)
        }
    }
    return ink
}
