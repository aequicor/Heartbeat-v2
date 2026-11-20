@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeHighlight
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeLine
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDiagramKind
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraDiagramPreviews
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraSourceBlock
import io.github.aequicor.heartbeat.features.aistudio.ui.components.LocalDiagramPreviews
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuroraSourceBlockTest {
    @Test
    fun codeWrapCopyAndFullscreenKeepTheSourceAndReturnFocus() {
        val line = "val result = " + List(24) { "requestId" }.joinToString(" + ")
        val code = ReplyBlock.Code(
            id = 0,
            language = "Kotlin",
            source = "$line\n  return result\n",
            lines = listOf(
                ReplyCodeLine(line, listOf(ReplyCodeHighlight(0, 3, ReplyCodeKind.Keyword))),
                ReplyCodeLine("  return result", listOf()),
                ReplyCodeLine("", listOf()),
            ),
            closed = true,
        )
        val clipboard = TestClipboard()
        val scene = blockScene(code, touch = false, clipboard = clipboard)
        try {
            val originalHeight = scene.awaitNode { scene.text(line) }.boundsInRoot.height
            val initialWrap = assertNotNull(scene.action("Перенос строк"))
            assertEquals("Выключено", initialWrap.config.getOrNull(SemanticsProperties.StateDescription))
            assertNotNull(scene.action("Горизонтальная прокрутка"))
            scene.capture("aurora-source-code-desktop")
            scene.click(initialWrap)
            val wrapped = assertNotNull(scene.text(line))
            assertTrue(wrapped.boundsInRoot.height > originalHeight)
            assertNull(scene.action("Горизонтальная прокрутка"))
            assertEquals("Включено", scene.action("Перенос строк")?.config
                ?.getOrNull(SemanticsProperties.StateDescription))

            scene.click(assertNotNull(scene.action("Копировать исходник")))
            assertEquals(code.source, clipboard.current?.text)
            assertNotNull(scene.action("Скопировано"))
            scene.click(assertNotNull(scene.action("Открыть на всё окно")))
            val close = assertNotNull(scene.action("Закрыть просмотр"))
            assertEquals(true, close.config.getOrNull(SemanticsProperties.Focused))
            assertEquals("Включено", scene.action("Перенос строк")?.config
                ?.getOrNull(SemanticsProperties.StateDescription))
            scene.capture("aurora-source-code-fullscreen", settle = true)
            scene.escape()
            assertNull(scene.action("Закрыть просмотр"))
            assertEquals(true, scene.action("Открыть на всё окно")?.config?.getOrNull(SemanticsProperties.Focused))
        } finally {
            scene.close()
        }
    }

    @Test
    fun mermaidTogglesSourceAndZoomsTheFullscreenPreview() {
        val source = "flowchart TD\nA[Running] --> B[Stopping]\nB --> C[Stopped]"
        val calls = AtomicInteger()
        val cache = AuroraDiagramPreviews { format, text ->
            assertEquals("Mermaid", format)
            assertEquals(source, text)
            calls.incrementAndGet()
            diagram()
        }
        val scene = blockScene(ReplyBlock.Diagram(0, ReplyDiagramKind.Mermaid, source, closed = true), true, cache)
        try {
            scene.awaitNode { scene.action("Mermaid") }
            scene.capture("aurora-source-mermaid-android")
            val sourceAction = assertNotNull(scene.action("Показать исходник"))
            assertTrue(sourceAction.boundsInRoot.width >= 48f)
            scene.click(sourceAction)
            scene.awaitNode { scene.text("A[Running] --> B[Stopping]") }
            assertNotNull(scene.action("Перенос строк"))
            scene.capture("aurora-source-mermaid-source-android")
            scene.click(assertNotNull(scene.action("Показать диаграмму")))
            scene.awaitNode { scene.action("Mermaid") }
            assertEquals(1, calls.get())

            scene.click(assertNotNull(scene.action("Открыть на всё окно")))
            val image = assertNotNull(scene.action("Mermaid"))
            val initialHeight = image.boundsInRoot.height
            scene.click(assertNotNull(scene.action("Увеличить масштаб")))
            assertTrue(assertNotNull(scene.action("Mermaid")).boundsInRoot.height > initialHeight)
            scene.capture("aurora-source-mermaid-fullscreen", settle = true)
            scene.click(assertNotNull(scene.action("Закрыть просмотр")))
            assertEquals(true, scene.action("Открыть на всё окно")?.config?.getOrNull(SemanticsProperties.Focused))
        } finally {
            scene.close()
        }
    }

    @Test
    fun failedPlantUmlKeepsTheOriginalAndRetryRendersOnlyThatDiagram() {
        val source = "@startuml\nRunning --> Stopping\nStopping --> Stopped\n@enduml\n"
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val cache = AuroraDiagramPreviews { format, text ->
            assertEquals("PlantUML", format)
            assertEquals(source, text)
            if (calls.incrementAndGet() == 1) {
                release.await()
                error("Fixture rendering failure")
            }
            diagram()
        }
        val clipboard = TestClipboard()
        val block = ReplyBlock.Diagram(0, ReplyDiagramKind.PlantUml, source, closed = true)
        val scene = blockScene(block, true, cache, clipboard)
        try {
            assertNotNull(scene.awaitNode { scene.text("Подготавливаю диаграмму…") })
            scene.capture("aurora-source-plantuml-loading-android")
            release.complete(Unit)
            scene.awaitNode { scene.text("Не удалось отобразить диаграмму") }
            scene.capture("aurora-source-plantuml-error-android")
            scene.click(assertNotNull(scene.action("Копировать исходник")))
            assertEquals(source, clipboard.current?.text)
            scene.click(assertNotNull(scene.action("Повторить отображение")))
            scene.awaitNode { scene.action("PlantUML") }
            assertEquals(2, calls.get())
            assertNull(scene.action("Повторить отображение"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun emptyDiagramShowsItsStateAndNeverStartsRendering() {
        val calls = AtomicInteger()
        val cache = AuroraDiagramPreviews { _, _ -> calls.incrementAndGet(); diagram() }
        val scene = blockScene(ReplyBlock.Diagram(0, ReplyDiagramKind.Mermaid, "", closed = true), true, cache)
        try {
            scene.awaitNode { scene.text("Нет данных для диаграммы") }
            val expand = assertNotNull(scene.action("Открыть на всё окно"))
            assertNotNull(expand.config.getOrNull(SemanticsProperties.Disabled))
            scene.click(expand)
            assertNull(scene.action("Закрыть просмотр"))
            assertEquals(0, calls.get())
            scene.capture("aurora-source-mermaid-empty-android")
        } finally {
            scene.close()
        }
    }

    private fun blockScene(
        block: ReplyBlock,
        touch: Boolean,
        previews: AuroraDiagramPreviews? = null,
        clipboard: TestClipboard = TestClipboard(),
    ) = ImageComposeScene(if (touch) 390 else 620, 480) {
        AuroraTestTheme(density = if (touch) HbDensity.Touch else HbDensity.Dense) {
            CompositionLocalProvider(LocalDiagramPreviews provides previews, LocalClipboardManager provides clipboard) {
                Box(Modifier.fillMaxSize().background(HbAuroraColors.Canvas).padding(12.dp)) {
                    SelectionContainer {
                        AuroraSourceBlock(block, touch, streaming = false)
                    }
                }
            }
        }
    }

    private class TestClipboard : ClipboardManager {
        var current: AnnotatedString? = null
            private set

        override fun getText() = current
        override fun setText(annotatedString: AnnotatedString) {
            current = annotatedString
        }
    }

    private fun diagram(): ImageBitmap {
        val bitmap = ImageBitmap(960, 210)
        val canvas = Canvas(bitmap)
        val tint = Paint().apply { color = Color(0xFFDDE6D7) }
        val ink = Paint().apply { color = Color(0xFF4D6B48); strokeWidth = 4f }
        listOf(20f, 340f, 660f).forEach { x ->
            canvas.drawRect(Rect(x, 40f, x + 244f, 170f), tint)
            canvas.drawLine(Offset(x + 44f, 105f), Offset(x + 200f, 105f), ink)
        }
        listOf(264f, 584f).forEach { x ->
            val tip = Offset(x + 76f, 105f)
            canvas.drawLine(Offset(x, 105f), tip, ink)
            canvas.drawLine(Offset(tip.x - 14f, 94f), tip, ink)
            canvas.drawLine(Offset(tip.x - 14f, 116f), tip, ink)
        }
        return bitmap
    }

    private fun ImageComposeScene.text(label: String) = node {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> label in value.text.lineSequence() }
    }

    private fun ImageComposeScene.action(label: String) = node {
        label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }

    private fun ImageComposeScene.node(matches: (SemanticsNode) -> Boolean) =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(matches) }

    private fun SemanticsNode.find(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (matches(this)) this else children.firstNotNullOfOrNull { it.find(matches) }

    private fun ImageComposeScene.awaitNode(find: () -> SemanticsNode?): SemanticsNode {
        val expiresAt = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < expiresAt) {
            frame()
            val node = find()
            if (node != null) return node
            Thread.sleep(5)
        }
        error("Expected source block state was not laid out")
    }

    private fun ImageComposeScene.click(node: SemanticsNode) {
        val point = node.boundsInRoot.center
        sendPointerEvent(PointerEventType.Move, point)
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        frame()
    }

    private fun ImageComposeScene.escape() {
        sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
        frame()
    }

    private fun ImageComposeScene.frame() = repeat(3) { render(System.nanoTime()).close() }

    private fun ImageComposeScene.capture(name: String, settle: Boolean = false) {
        if (settle) {
            repeat(12) {
                Thread.sleep(20)
                render(System.nanoTime()).close()
            }
        }
        val directory = File("build/screenshots").apply { mkdirs() }
        render(System.nanoTime()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { File(directory, "$name.png").writeBytes(it.bytes) }
        }
    }
}
