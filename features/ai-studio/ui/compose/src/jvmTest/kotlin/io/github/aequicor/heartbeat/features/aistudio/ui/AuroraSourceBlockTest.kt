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
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbProvideWindowCaption
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
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
import javax.imageio.ImageIO
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
        val scene = blockScene(code, touch = false, clipboard = clipboard, caption = windowsCaption())
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
            assertTrue(close.boundsInRoot.top >= 32f)
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
    fun fullscreenDiagramCentersAndPansSmallContentWhileTheAppBarStaysFixed() {
        val source = "flowchart LR\nA --> B"
        val cache = AuroraDiagramPreviews { _, _ -> ImageBitmap(360, 180) }
        val scene = blockScene(
            ReplyBlock.Diagram(0, ReplyDiagramKind.Mermaid, source, closed = true),
            touch = false, previews = cache, caption = windowsCaption(),
        )
        try {
            scene.awaitNode { scene.action("Mermaid") }
            scene.click(assertNotNull(scene.action("Открыть на всё окно")))
            val close = assertNotNull(scene.action("Закрыть просмотр"))
            val toolbar = close.boundsInRoot
            assertTrue(toolbar.top >= 32f)
            listOf("Уменьшить масштаб", "Увеличить масштаб", "Показать исходник", "Копировать исходник").forEach {
                assertEquals(toolbar.center.y, assertNotNull(scene.action(it)).boundsInRoot.center.y)
            }
            val original = assertNotNull(scene.action("Mermaid")).boundsInRoot
            assertEquals(310f, original.center.x, .5f)
            assertEquals(284f, original.center.y, .5f)
            scene.capture("aurora-viewer-windows-centered", settle = true)

            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown, isShiftPressed = true))
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyUp, isShiftPressed = true))
            scene.frame()
            assertEquals(true, scene.action("Копировать исходник")?.config?.getOrNull(SemanticsProperties.Focused))
            scene.awaitNode { scene.text("Копировать исходник") }
            scene.escape()
            assertNotNull(scene.action("Закрыть просмотр"))
            assertNull(scene.text("Копировать исходник"))

            val start = original.center
            scene.sendPointerEvent(PointerEventType.Move, start)
            scene.sendPointerEvent(PointerEventType.Press, start, button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Move, start + Offset(25f, 0f))
            scene.frame()
            scene.sendPointerEvent(PointerEventType.Move, start + Offset(90f, 70f))
            scene.sendPointerEvent(
                PointerEventType.Release, start + Offset(90f, 70f), button = PointerButton.Primary,
            )
            scene.frame()
            val moved = assertNotNull(scene.action("Mermaid")).boundsInRoot
            assertTrue(moved.center.x > original.center.x + 40f)
            assertTrue(moved.center.y > original.center.y + 40f)
            assertEquals(toolbar, scene.action("Закрыть просмотр")?.boundsInRoot)
            scene.sendPointerEvent(PointerEventType.Move, moved.center + Offset(15f, 15f))
            scene.frame()
            assertEquals(moved, scene.action("Mermaid")?.boundsInRoot)
            scene.capture("aurora-viewer-windows-panned")

            scene.click(assertNotNull(scene.action("Показать исходник")))
            scene.awaitNode { scene.text("A --> B") }
            assertNull(scene.action("Увеличить масштаб"))
            scene.click(assertNotNull(scene.action("Показать диаграмму")))
            assertEquals(moved, assertNotNull(scene.action("Mermaid")).boundsInRoot)
            scene.sendPointerEvent(PointerEventType.Scroll, moved.center, scrollDelta = Offset(0f, 1f))
            scene.awaitNode { scene.action("Mermaid")?.takeIf { it.boundsInRoot.center.y < moved.center.y } }
            assertEquals(toolbar, scene.action("Закрыть просмотр")?.boundsInRoot)

            scene.escape()
            assertNull(scene.action("Закрыть просмотр"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun oversizedDiagramStartsAtItsCenterAndZoomControlsRespectBothLimits() {
        val bitmap = ImageBitmap(1600, 1200)
        Canvas(bitmap).drawRect(Rect(780f, 580f, 820f, 620f), Paint().apply { color = Color(0xFF1B5875) })
        val scene = blockScene(
            ReplyBlock.Diagram(0, ReplyDiagramKind.Mermaid, "flowchart LR\nA --> B", closed = true),
            touch = false, previews = AuroraDiagramPreviews { _, _ -> bitmap }, caption = windowsCaption(),
        )
        try {
            scene.awaitNode { scene.action("Mermaid") }
            scene.click(assertNotNull(scene.action("Открыть на всё окно")))
            scene.capture("aurora-viewer-windows-oversized", settle = true)
            val rendered = ImageIO.read(File("build/screenshots/aurora-viewer-windows-oversized.png"))
            assertEquals(0x1B5875, rendered.getRGB(310, 284) and 0xFFFFFF)
            repeat(16) { scene.click(assertNotNull(scene.action("Увеличить масштаб"))) }
            assertNotNull(scene.action("Увеличить масштаб")?.config?.getOrNull(SemanticsProperties.Disabled))
            assertNotNull(scene.text("400%"))
            repeat(20) { scene.click(assertNotNull(scene.action("Уменьшить масштаб"))) }
            assertNotNull(scene.action("Уменьшить масштаб")?.config?.getOrNull(SemanticsProperties.Disabled))
            assertNotNull(scene.text("25%"))
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

    @Test
    fun controlWheelZoomsAroundThePointerAndCenterRestoresTheInitialView() {
        val scene = blockScene(
            ReplyBlock.Diagram(0, ReplyDiagramKind.Mermaid, "flowchart LR\nA --> B", closed = true),
            touch = false, previews = AuroraDiagramPreviews { _, _ -> ImageBitmap(360, 180) },
            caption = windowsCaption(),
        )
        try {
            scene.awaitNode { scene.action("Mermaid") }
            scene.click(assertNotNull(scene.action("Открыть на всё окно")))
            val original = assertNotNull(scene.action("Mermaid")).boundsInRoot
            val center = original.center
            assertNull(scene.action("Горизонтальная прокрутка диаграммы"))
            assertNull(scene.action("Вертикальная прокрутка диаграммы"))
            scene.controlWheel(center + Offset(60f, 40f), -1f)
            assertNotNull(scene.text("125%"))
            val zoomed = assertNotNull(scene.action("Mermaid")).boundsInRoot
            assertEquals(center.x - 15f, zoomed.center.x, .5f)
            assertEquals(center.y - 10f, zoomed.center.y, .5f)
            scene.click(assertNotNull(scene.action("Центрировать")))
            assertEquals(original, assertNotNull(scene.action("Mermaid")).boundsInRoot)
            assertNotNull(scene.text("100%"))
            scene.controlWheel(center, 1f)
            assertNotNull(scene.text("80%"))
            scene.click(assertNotNull(scene.action("Центрировать")))
            assertNotNull(scene.text("100%"))
            scene.drag(center, Offset(430f, 240f))
            assertNotNull(scene.action("Горизонтальная прокрутка диаграммы"))
            assertNotNull(scene.action("Вертикальная прокрутка диаграммы"))
            scene.click(assertNotNull(scene.action("Центрировать")))
            assertEquals(center, scene.action("Mermaid")?.boundsInRoot?.center)
            assertNull(scene.action("Горизонтальная прокрутка диаграммы"))
            assertNull(scene.action("Вертикальная прокрутка диаграммы"))
            scene.controlWheel(center, -40f)
            assertNotNull(scene.text("400%"))
            scene.controlWheel(center, 80f)
            assertNotNull(scene.text("25%"))
            scene.click(assertNotNull(scene.action("Центрировать")))
            assertNotNull(scene.text("100%"))
            assertEquals(original, assertNotNull(scene.action("Mermaid")).boundsInRoot)
        } finally {
            scene.close()
        }
    }

    @Test
    fun detailedRasterKeepsTheDiagramSizeAndPosition() {
        val detail = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cache = AuroraDiagramPreviews(render = { _, _, scale ->
            if (scale > 2f) {
                detail.complete(Unit)
                release.await()
            }
            ImageBitmap((60 * scale).toInt(), (30 * scale).toInt())
        })
        val scene = blockScene(
            ReplyBlock.Diagram(0, ReplyDiagramKind.Mermaid, "flowchart LR\nA --> B", closed = true),
            touch = false, previews = cache, caption = windowsCaption(),
        )
        try {
            scene.awaitNode { scene.action("Mermaid") }
            scene.click(assertNotNull(scene.action("Открыть на всё окно")))
            scene.controlWheel(Offset(350f, 300f), -40f)
            val before = assertNotNull(scene.action("Mermaid")).boundsInRoot
            scene.awaitNode { scene.action("Mermaid")?.takeIf { detail.isCompleted } }
            release.complete(Unit)
            scene.capture("aurora-viewer-detailed", settle = true)
            assertEquals(before, assertNotNull(scene.action("Mermaid")).boundsInRoot)
            assertNotNull(scene.text("400%"))
        } finally {
            release.complete(Unit)
            scene.close()
        }
    }

    @Test
    fun scrollbarsFollowOverflowIndependentlyAndTheirThumbsMoveTheDiagram() {
        val cases = listOf(
            Triple(ImageBitmap(1600, 1200), true, true),
            Triple(ImageBitmap(1600, 180), true, false),
            Triple(ImageBitmap(360, 1200), false, true),
            Triple(ImageBitmap(1176, 720), false, false),
        )
        for ((bitmap, horizontal, vertical) in cases) {
            val scene = blockScene(
                ReplyBlock.Diagram(0, ReplyDiagramKind.Mermaid, "flowchart LR\nA --> B", closed = true),
                touch = false, previews = AuroraDiagramPreviews { _, _ -> bitmap }, caption = windowsCaption(),
            )
            try {
                scene.awaitNode { scene.action("Mermaid") }
                scene.click(assertNotNull(scene.action("Открыть на всё окно")))
                val h = scene.action("Горизонтальная прокрутка диаграммы")
                val v = scene.action("Вертикальная прокрутка диаграммы")
                assertEquals(horizontal, h != null)
                assertEquals(vertical, v != null)
                if (h != null) {
                    val range = requireNotNull(h.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange))
                    val before = range.value()
                    assertEquals(range.maxValue() / 2, before, .5f)
                    scene.drag(h.boundsInRoot.center, Offset(60f, 0f))
                    assertTrue(range.value() > before)
                    scene.click(assertNotNull(scene.action("Центрировать")))
                }
                if (v != null) {
                    val current = assertNotNull(scene.action("Вертикальная прокрутка диаграммы"))
                    val range = requireNotNull(current.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
                    val before = range.value()
                    scene.drag(current.boundsInRoot.center, Offset(0f, 55f))
                    assertTrue(range.value() > before)
                    scene.click(assertNotNull(scene.action("Центрировать")))
                }
            } finally {
                scene.close()
            }
        }
    }

    private fun blockScene(
        block: ReplyBlock,
        touch: Boolean,
        previews: AuroraDiagramPreviews? = null,
        clipboard: TestClipboard = TestClipboard(),
        caption: HbWindowCaption = HbWindowCaption(0.dp, 0.dp) { _, _ -> },
    ) = ImageComposeScene(if (touch) 390 else 620, 480) {
        AuroraTestTheme(density = if (touch) HbDensity.Touch else HbDensity.Dense) {
            HbProvideWindowCaption(caption) {
                CompositionLocalProvider(
                    LocalDiagramPreviews provides previews,
                    LocalClipboardManager provides clipboard,
                ) {
                    Box(Modifier.fillMaxSize().background(HbAuroraColors.Canvas).padding(12.dp)) {
                        SelectionContainer {
                            AuroraSourceBlock(block, touch, streaming = false)
                        }
                    }
                }
            }
        }
    }

    private fun windowsCaption() = HbWindowCaption(138.dp, 32.dp) { _, _ -> }

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

    private fun ImageComposeScene.controlWheel(point: Offset, delta: Float) {
        sendPointerEvent(
            PointerEventType.Scroll, point, scrollDelta = Offset(0f, delta),
            keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true),
        )
        frame()
    }

    private fun ImageComposeScene.drag(from: Offset, by: Offset) {
        sendPointerEvent(PointerEventType.Move, from)
        sendPointerEvent(PointerEventType.Press, from, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Move, from + by * .4f)
        frame()
        sendPointerEvent(PointerEventType.Move, from + by)
        sendPointerEvent(PointerEventType.Release, from + by, button = PointerButton.Primary)
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
