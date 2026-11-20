@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraAgentMessage
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraMessageMenu
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraUserBubble
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

class AuroraMessageTimestampTest {

    @Test
    fun desktopUserKeepsReadingAndMetadataRolesAndItsTimestampInTheFooter() {
        val text = "Сначала запрашиваем состояние текущего хода.\nГотово."
        val time = "14:06"
        var bubbleWidth = 0
        var bubbleHeight = 0
        val scene = ImageComposeScene(620, 200) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                Box(Modifier.onSizeChanged { bubbleWidth = it.width; bubbleHeight = it.height }) {
                    AuroraUserBubble(User("prompt", time, text), false, {}, rememberSelectionState())
                }
            }
        }
        try {
            scene.render().close()
            val root = scene.semanticsOwners.single().rootSemanticsNode
            val body = checkNotNull(root.textNode(text))
            val stamp = checkNotNull(root.textNode(time))
            val bodyStyle = body.textLayout().layoutInput.style
            val stampStyle = stamp.textLayout().layoutInput.style
            assertEquals(AuroraTestFonts.ui, bodyStyle.fontFamily)
            assertEquals(15.sp, bodyStyle.fontSize)
            assertEquals(22.sp, bodyStyle.lineHeight)
            assertEquals(HbAuroraColors.DesktopInk, bodyStyle.color)
            assertEquals(bodyStyle.fontFamily, stampStyle.fontFamily)
            assertEquals(bodyStyle.fontWeight, stampStyle.fontWeight)
            assertEquals(12.sp, stampStyle.fontSize)
            assertEquals(16.sp, stampStyle.lineHeight)
            assertEquals(HbAuroraLayout.DesktopMessageHorizontalInset.value, body.boundsInRoot.left)
            assertEquals(HbAuroraLayout.DesktopMessageVerticalInset.value, body.boundsInRoot.top)
            assertEquals(bubbleWidth - HbAuroraLayout.DesktopMessageHorizontalInset.value, stamp.boundsInRoot.right)
            assertEquals(body.boundsInRoot.bottom + HbAuroraLayout.SmallGap.value, stamp.boundsInRoot.top)
            assertEquals(HbAuroraLayout.DesktopMessageVerticalInset.value, bubbleHeight - stamp.boundsInRoot.bottom)
        } finally {
            scene.close()
        }
    }

    @Test
    fun longCyrillicAndUnbrokenTextRespectDesktopAndTouchBubbleWidthLimits() {
        val examples = listOf(
            "Длинное сообщение проверяет перенос кириллических слов и полное отображение текста. ".repeat(4),
            "ДлиннаяСтрокаБезПробелов".repeat(8),
        )
        for (touch in listOf(false, true)) for (scale in listOf(1f, 2f)) for (text in examples) {
            verifyBubbleWidth(touch, scale, text)
        }
    }

    private fun verifyBubbleWidth(touch: Boolean, scale: Float, text: String) {
        var bubbleWidth = 0
        val scene = ImageComposeScene(620, 1200) {
            AuroraTestTheme(textScale = scale, density = if (touch) HbDensity.Touch else HbDensity.Dense) {
                Box(Modifier.onSizeChanged { bubbleWidth = it.width }) {
                    AuroraUserBubble(User("long-prompt", "14:06", text), touch, {}, rememberSelectionState())
                }
            }
        }
        try {
            scene.render().close()
            val root = scene.semanticsOwners.single().rootSemanticsNode
            val body = checkNotNull(root.textNode(text))
            val stamp = checkNotNull(root.textNode("14:06"))
            val layout = body.textLayout()
            val maximum = (if (touch) HbAuroraLayout.UserBubble else HbAuroraLayout.DesktopMessageBubble)
                .value.toInt()
            val inset = (if (touch) HbAuroraLayout.ContentInset else HbAuroraLayout.DesktopMessageHorizontalInset)
                .value.toInt()
            val textMaximum = maximum - inset * 2
            val widestLine = (0 until layout.lineCount).maxOf(layout::getLineRight)
            assertEquals(textMaximum, layout.layoutInput.constraints.maxWidth)
            assertTrue(layout.lineCount > 1, "Both sentences and unbroken text must wrap")
            assertTrue(widestLine <= textMaximum, "Every rendered line fits the available text width")
            assertTrue(bubbleWidth <= maximum, "The bubble respects the platform's maximum width")
            assertEquals(ceil(widestLine).toInt() + inset * 2, bubbleWidth)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            assertTrue(!layout.hasVisualOverflow, "Wrapping keeps the complete message visible")
            assertEquals(inset.toFloat(), body.boundsInRoot.left)
            assertEquals(
                (if (touch) HbAuroraLayout.Space else HbAuroraLayout.DesktopMessageVerticalInset).value,
                body.boundsInRoot.top,
            )
            assertEquals(bubbleWidth - inset.toFloat(), stamp.boundsInRoot.right)
            assertEquals(body.boundsInRoot.bottom + 4f, stamp.boundsInRoot.top)
        } finally {
            scene.close()
        }
    }

    @Test
    fun desktopReplyAlignsItsTimestampWithTheLastLineWhenItFits() {
        verifyTimestamp("Сначала запрашиваем состояние текущего хода.\nГотово.") { body, stamp, _, _ ->
            val bodyText = body.textLayout()
            val time = stamp.textLayout()
            val last = bodyText.lineCount - 1
            val gap = stamp.positionInRoot.x - (body.positionInRoot.x + bodyText.getLineRight(last))
            val textBaseline = body.positionInRoot.y + bodyText.getLineBaseline(last)
            val timeBaseline = stamp.positionInRoot.y + time.getLineBaseline(0)

            assertTrue(gap >= 8f)
            assertTrue(abs(textBaseline - timeBaseline) <= 1f)
        }
    }

    @Test
    fun aWrappedDesktopReplyFitsItsLongestRenderedLine() {
        val text = "Повторный запрос можно отправить после подтверждения остановки. Иначе оба " +
            "запроса могут выполняться одновременно.\n\n" +
            "Дождитесь статуса «Остановлено», затем отправьте запрос снова."
        verifyTimestamp(text, captureName = "aurora-wrapped-reply-insets") { body, stamp, bubbleWidth, _ ->
            val layout = body.textLayout()
            val widestLine = (0 until layout.lineCount).maxOf(layout::getLineRight)
            val textMaximum = HbAuroraLayout.DesktopAgentText.value.toInt()
            val inset = HbAuroraLayout.DesktopMessageHorizontalInset.value
            assertEquals(textMaximum, layout.layoutInput.constraints.maxWidth)
            assertTrue(layout.getLineEnd(0) < text.indexOf('\n'), "The first paragraph wraps within the reading column")
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            assertTrue(!layout.hasVisualOverflow)
            assertTrue(widestLine <= textMaximum)
            assertEquals(ceil(widestLine).toInt() + inset.toInt() * 2, bubbleWidth)
            assertEquals(bubbleWidth - inset, stamp.boundsInRoot.right)
        }
    }

    @Test
    fun veryNarrowBubblesKeepTheirWidthAndShowTheFullTimestampBelow() {
        verifyNarrowBubble("Текст\nа", user = true, external = false)
        verifyNarrowBubble("i\na", user = true, external = true)
        verifyNarrowBubble("i\na", user = true, external = true, touch = false)
        verifyNarrowBubble("Я", user = false, external = true, scale = 2f)
        verifyNarrowBubble("Я", user = false, external = true, scale = 2f, touch = false)
    }

    private fun verifyNarrowBubble(
        text: String,
        user: Boolean,
        external: Boolean,
        scale: Float = 1f,
        touch: Boolean = true,
    ) {
        var time by mutableStateOf("")
        var bubbleWidth = 0
        var bubbleHeight = 0
        val scene = ImageComposeScene(320, 200) {
            AuroraTestTheme(textScale = scale, density = if (touch) HbDensity.Touch else HbDensity.Dense) {
                val prompt = User("prompt", time, text)
                val reply = Reply("reply", time, text = text, status = ReplyStatus.Completed)
                val selection = rememberSelectionState()
                Box(Modifier.fillMaxSize().background(HbAuroraColors.Canvas)) {
                    AuroraMessageMenu(if (user) prompt else reply, touch, {}, 320.dp, selection) {
                        Box(Modifier.onSizeChanged { size ->
                            bubbleWidth = size.width
                            bubbleHeight = size.height
                        }) {
                            if (user) AuroraUserBubble(prompt, touch, {}, selection)
                            else AuroraAgentMessage(reply, false, setOf(), setOf(), touch, {}, selection = selection)
                        }
                    }
                }
            }
        }
        try {
            scene.render().close()
            val original = bubbleWidth
            time = "14:06"
            scene.render().close()
            val root = scene.semanticsOwners.single().rootSemanticsNode
            val stamp = root.visibleTextNodes(time).single()
            val layout = stamp.textLayout()
            val body = checkNotNull(root.textNode(text))
            val bodyStyle = body.textLayout().layoutInput.style
            val stampStyle = layout.layoutInput.style
            assertEquals(AuroraTestFonts.ui, bodyStyle.fontFamily)
            assertEquals((if (touch) 16 else 15).times(scale).sp, bodyStyle.fontSize)
            assertEquals((if (touch) 24 else 22).times(scale).sp, bodyStyle.lineHeight)
            assertEquals(bodyStyle.fontFamily, stampStyle.fontFamily)
            assertEquals(bodyStyle.fontWeight, stampStyle.fontWeight)
            assertEquals((if (touch) 13 else 12).times(scale).sp, stampStyle.fontSize)
            assertEquals((if (touch) 18 else 16).times(scale).sp, stampStyle.lineHeight)
            assertEquals(original, bubbleWidth)
            assertEquals(1, layout.lineCount)
            assertEquals(time.length, layout.getLineEnd(0, visibleEnd = true))
            val textBottom = body.boundsInRoot.bottom
            assertTrue(stamp.boundsInRoot.top >= (if (external) bubbleHeight.toFloat() else textBottom) + 4f)
            assertTrue(stamp.boundsInRoot.width >= layout.getLineRight(0))
            assertTrue(stamp.boundsInRoot.left >= 0f && stamp.boundsInRoot.right <= 320f)
            val role = if (user) "user" else "agent"
            val platform = if (touch) "touch" else "desktop"
            scene.capture("aurora-timestamp-$role-$external-$platform")
        } finally {
            scene.close()
        }
    }

    @Test
    fun fallsBackForShortTextMixedDirectionAndAnExplicitEmptyLastLine() {
        val examples = listOf(
            "Да. Черновик сохранён.",
            "Сначала запрашиваем состояние текущего хода.\nХод אבג",
            "Сначала запрашиваем состояние текущего хода.\nГотово.\n",
        )
        examples.forEach { text ->
            verifyTimestamp(text) { body, stamp, width, height ->
                assertTrue(stamp.boundsInRoot.top >= body.boundsInRoot.bottom + 4f, text)
                if (text == examples.first()) {
                    assertEquals(
                        body.boundsInRoot.width + HbAuroraLayout.DesktopMessageHorizontalInset.value * 2,
                        width.toFloat(),
                    )
                    assertEquals(
                        body.boundsInRoot.height + stamp.boundsInRoot.height + HbAuroraLayout.SmallGap.value +
                            HbAuroraLayout.DesktopMessageVerticalInset.value * 2,
                        height.toFloat(),
                    )
                }
            }
        }
    }

    private fun verifyTimestamp(
        text: String,
        captureName: String? = null,
        verify: (SemanticsNode, SemanticsNode, Int, Int) -> Unit,
    ) {
        val time = "14:06"
        var bubbleWidth = 0
        var bubbleHeight = 0
        val scene = ImageComposeScene(640, 280) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                AuroraAgentMessage(
                    Reply("timestamp-example", time, text = text, status = ReplyStatus.Completed),
                    expanded = false, expandedSteps = setOf(), expandedEvents = setOf(), touch = false,
                    onAction = {}, modifier = Modifier.onSizeChanged {
                        bubbleWidth = it.width
                        bubbleHeight = it.height
                    },
                )
            }
        }
        try {
            scene.render().close()
            if (captureName != null) scene.capture(captureName)
            val root = scene.semanticsOwners.single().rootSemanticsNode
            val body = checkNotNull(root.textNode(text))
            val timestamp = checkNotNull(root.textNode(time))
            val bodyStyle = body.textLayout().layoutInput.style
            val timestampStyle = timestamp.textLayout().layoutInput.style
            assertEquals(AuroraTestFonts.ui, bodyStyle.fontFamily)
            assertEquals(15.sp, bodyStyle.fontSize)
            assertEquals(22.sp, bodyStyle.lineHeight)
            assertEquals(HbAuroraColors.DesktopInk, bodyStyle.color)
            assertEquals(bodyStyle.fontFamily, timestampStyle.fontFamily)
            assertEquals(bodyStyle.fontWeight, timestampStyle.fontWeight)
            assertEquals(12.sp, timestampStyle.fontSize)
            assertEquals(16.sp, timestampStyle.lineHeight)
            assertEquals(HbAuroraLayout.DesktopMessageHorizontalInset.value, body.boundsInRoot.left)
            assertEquals(HbAuroraLayout.DesktopMessageVerticalInset.value, body.boundsInRoot.top)
            verify(body, timestamp, bubbleWidth, bubbleHeight)
        } finally {
            scene.close()
        }
    }

    private fun SemanticsNode.textNode(text: String): SemanticsNode? = visibleTextNodes(text).singleOrNull()

    private fun SemanticsNode.visibleTextNodes(text: String): List<SemanticsNode> {
        val visible = config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text == text && layoutInfo.isPlaced
        return if (visible) listOf(this) else children.flatMap { it.visibleTextNodes(text) }
    }

    private fun ImageComposeScene.capture(name: String) {
        val directory = File("build/screenshots").apply { mkdirs() }
        render().use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use {
                File(directory, "$name.png").writeBytes(it.bytes)
            }
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        check(config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) == true)
        return results.single()
    }
}
