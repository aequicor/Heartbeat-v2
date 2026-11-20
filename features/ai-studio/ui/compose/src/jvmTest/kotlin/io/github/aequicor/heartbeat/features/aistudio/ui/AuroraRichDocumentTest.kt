@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyAlignment
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyInline
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraRichDocument
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuroraRichDocumentTest {
    @Test
    fun plainReplyKeepsTheOriginalLayoutCallback() {
        val source = "Разбор отмены\nСохранение вложений"
        var layout: TextLayoutResult? = null
        ImageComposeScene(390, 180) {
            AuroraTestTheme {
                AuroraRichDocument(
                    ReplyDocument.plain(source), touch = false, rememberSelectionState(),
                    onTextLayout = { layout = it },
                )
            }
        }.withScene { scene ->
            scene.frame()
            assertEquals(source, assertNotNull(layout).layoutInput.text.text)
            assertEquals(2, layout.lineCount)
            assertNotNull(scene.text(source))
        }
    }

    @Test
    fun richReplyRendersDocumentRolesAndOpensOnlySafeLinksOnActivation() {
        val openedUrls = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUrls += uri
            }
        }
        documentScene(620, touch = false, uriHandler).withScene { scene ->
            scene.awaitDocument()
            val heading = assertNotNull(scene.text("Отмена запроса"))
            assertNotNull(heading.config.getOrNull(SemanticsProperties.Heading))
            val taskMarker = assertNotNull(scene.node {
                it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
            })
            assertNull(taskMarker.config.getOrNull(SemanticsActions.OnClick))
            assertNotNull(scene.text("Полученный текст сохраняется после остановки."))
            assertEquals(listOf(), openedUrls)
            val paragraph = assertNotNull(scene.text("Ссылка и команда"))
            assertEquals(8f, paragraph.boundsInRoot.top - heading.boundsInRoot.bottom)
            val annotated = paragraph.config[SemanticsProperties.Text].single()
            assertEquals(1, annotated.getLinkAnnotations(0, annotated.length).size)
            scene.click(Offset(paragraph.boundsInRoot.left + 15, paragraph.boundsInRoot.center.y))
            assertEquals(listOf("https://example.org/guide"), openedUrls)
            scene.capture("aurora-rich-document-desktop")
        }
    }

    @Test
    fun mobileTableScrollsInsideItsOwnViewportAndKeepsTheToolbar() {
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) = error("Unexpected navigation")
        }
        val clock = AuroraHoverTestClock()
        documentScene(390, touch = true, uriHandler, context = clock.context).withScene { scene ->
            val frame = { clock.frame(scene) }
            scene.awaitDocument(frame)
            val copy = assertNotNull(scene.action("Копировать таблицу"))
            assertTrue(copy.boundsInRoot.width >= 48f)
            val originalToolbar = copy.boundsInRoot
            val originalLeadingText = assertNotNull(scene.text("Подтверждение")).boundsInRoot
            val opener = assertNotNull(scene.action("Открыть на всё окно"))
            val inlineScroll = assertNotNull(opener.root().find { it.hasHorizontalScroll() })
            scene.scrollRight(inlineScroll, clock)
            val inlinePosition = inlineScroll.horizontalPosition()
            assertTrue(inlinePosition > 0f)
            assertTrue(assertNotNull(scene.text("Подтверждение")).boundsInRoot.left < originalLeadingText.left)
            assertEquals(originalToolbar, scene.action("Копировать таблицу")?.boundsInRoot)
            scene.capture("aurora-rich-document-android", clock.nanoTime)

            scene.click(opener.boundsInRoot.center, frame)
            val close = assertNotNull(scene.action("Закрыть просмотр"))
            val modalScroll = assertNotNull(close.root().find { it.hasHorizontalScroll() })
            scene.scrollRight(modalScroll, clock)
            val modalPosition = modalScroll.horizontalPosition()
            assertTrue(modalPosition > 0f)
            scene.click(close.boundsInRoot.center, frame)
            clock.advance(scene, 250)
            assertNull(scene.action("Закрыть просмотр"))
            val reopenedButton = assertNotNull(scene.action("Открыть на всё окно"))
            assertEquals(true, reopenedButton.config.getOrNull(SemanticsProperties.Focused))
            assertEquals(
                inlinePosition,
                assertNotNull(reopenedButton.root().find { it.hasHorizontalScroll() }).horizontalPosition(),
            )

            scene.click(reopenedButton.boundsInRoot.center, frame)
            val reopenedClose = scene.awaitNode(frame) { scene.action("Закрыть просмотр") }
            assertEquals(
                modalPosition,
                assertNotNull(reopenedClose.root().find { it.hasHorizontalScroll() }).horizontalPosition(),
            )
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
            frame()
            assertNull(scene.action("Закрыть просмотр"))
            assertNotNull(scene.action("Открыть на всё окно"))
        }
    }

    @Test
    fun nestedTablesUseTheAvailableWidthAndEmptyBlocksDoNotAddControls() {
        val inlineTable = document.blocks.last() as ReplyBlock.Table
        val nested = ReplyDocument(
            listOf(
                document.blocks.first(),
                ReplyBlock.Paragraph(1, listOf()),
                ReplyBlock.Table(2, listOf(), listOf(), listOf()),
                ReplyBlock.ListBlock(
                    3, ordered = true, start = 7, items = listOf(
                        listOf(
                            ReplyBlock.Paragraph(4, inline("Проверить результаты остановки")),
                            ReplyBlock.Quote(5, listOf(inlineTable)),
                        ),
                    ),
                ),
            ),
        )
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) = error("Unexpected navigation")
        }
        documentScene(320, touch = true, uriHandler, nested).withScene { scene ->
            scene.awaitDocument()
            val title = assertNotNull(scene.text("Отмена запроса"))
            val marker = assertNotNull(scene.text("7."))
            val item = assertNotNull(scene.text("Проверить результаты остановки"))
            val table = assertNotNull(scene.text("Таблица"))
            assertEquals(8f, marker.boundsInRoot.top - title.boundsInRoot.bottom)
            assertTrue(item.boundsInRoot.left > marker.boundsInRoot.left)
            assertTrue(table.boundsInRoot.left > item.boundsInRoot.left)
            val dialogTrigger = assertNotNull(scene.action("Открыть на всё окно"))
            assertTrue(dialogTrigger.boundsInRoot.right <= 308f)
            scene.capture("aurora-rich-document-nested")
        }
    }

    private fun SemanticsNode.root(): SemanticsNode = parent?.root() ?: this

    private fun SemanticsNode.hasHorizontalScroll(): Boolean =
        config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null

    private fun SemanticsNode.horizontalPosition() =
        config[SemanticsProperties.HorizontalScrollAxisRange].value()

    private fun ImageComposeScene.scrollRight(scroller: SemanticsNode, clock: AuroraHoverTestClock) {
        assertTrue(assertNotNull(scroller.config[SemanticsActions.ScrollBy].action).invoke(72f, 0f))
        repeat(24) { clock.advance(this, 16) }
    }

    private fun documentScene(
        width: Int,
        touch: Boolean,
        uriHandler: UriHandler,
        reply: ReplyDocument = document,
        context: CoroutineContext = Dispatchers.Unconfined,
    ) = ImageComposeScene(width, 640, coroutineContext = context) {
        AuroraTestTheme(density = if (touch) HbDensity.Touch else HbDensity.Dense) {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                Box(Modifier.fillMaxSize().background(HbAuroraColors.Canvas).padding(12.dp)) {
                    AuroraRichDocument(reply, touch, rememberSelectionState())
                }
            }
        }
    }

    private inline fun ImageComposeScene.withScene(check: (ImageComposeScene) -> Unit) {
        try {
            check(this)
        } finally {
            close()
        }
    }

    private fun ImageComposeScene.frame() = repeat(3) { render(System.nanoTime()).close() }

    private fun ImageComposeScene.awaitDocument(renderFrame: () -> Unit = { frame() }) {
        awaitNode(renderFrame) { text("Отмена запроса") }
    }

    private fun ImageComposeScene.awaitNode(renderFrame: () -> Unit, find: () -> SemanticsNode?): SemanticsNode {
        val expiresAt = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < expiresAt) {
            renderFrame()
            val match = find()
            if (match != null) return match
            Thread.sleep(5)
        }
        error("Expected document node was not laid out")
    }

    private fun ImageComposeScene.click(point: Offset, renderFrame: () -> Unit = { frame() }) {
        sendPointerEvent(PointerEventType.Move, point)
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        renderFrame()
    }

    private fun ImageComposeScene.text(label: String) = node {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == label }
    }

    private fun ImageComposeScene.action(label: String) = node {
        label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }

    private fun ImageComposeScene.node(matches: (SemanticsNode) -> Boolean) =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(matches) }

    private fun SemanticsNode.find(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (matches(this)) this else children.firstNotNullOfOrNull { it.find(matches) }

    private fun ImageComposeScene.capture(name: String, nanoTime: Long = System.nanoTime()) {
        val directory = File("build/screenshots").apply { mkdirs() }
        render(nanoTime).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { File(directory, "$name.png").writeBytes(it.bytes) }
        }
    }

    private fun inline(vararg values: String) = values.map { value -> listOf(ReplyInline(value)) }

    private val document = ReplyDocument(
        listOf(
            ReplyBlock.Heading(0, 1, listOf(ReplyInline("Отмена запроса"))),
            ReplyBlock.Paragraph(1, listOf(listOf(
                ReplyInline("Ссылка", url = "https://example.org/guide"),
                ReplyInline(" и "),
                ReplyInline("команда", code = true, url = "javascript:alert(1)"),
            ))),
            ReplyBlock.Quote(2, listOf(
                ReplyBlock.Paragraph(3, inline("Полученный текст сохраняется после остановки.")),
            )),
            ReplyBlock.ListBlock(
                4, ordered = false, start = 1, items = listOf(
                    listOf(ReplyBlock.Paragraph(5, inline("Проверен исходный запрос"))),
                    listOf(ReplyBlock.Paragraph(6, inline("Проверить повторную отправку"))),
                ), checked = listOf(true, false),
            ),
            ReplyBlock.Table(
                7, inline("Состояние", "Подтверждение", "Вложения", "Действие"),
                listOf(
                    inline("running", "Получен ответ от движка", "Сохранены", "Остановить"),
                    inline("stopped", "Остановка подтверждена", "Доступны для отправки", "Повторить"),
                ),
                listOf(ReplyAlignment.Left, ReplyAlignment.Left, ReplyAlignment.Center, ReplyAlignment.Right),
            ),
        ),
    )
}
