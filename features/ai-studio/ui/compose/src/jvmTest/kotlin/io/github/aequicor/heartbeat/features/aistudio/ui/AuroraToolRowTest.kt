@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkEvent
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuroraToolRowTest {
    @Test
    fun desktopToolFitsItsStatusAndRevealsTheFullTitleOnClick() {
        val clock = AuroraHoverTestClock()
        val name = "Читаю контракт сессии и проверяю восстановление связи после остановки"
        val event = pencilTimelineFixture().steps.first().events.first().copy(title = name)
        val label = checkNotNull(event.detailLabel)
        var expanded by mutableStateOf(false)
        val scene = ImageComposeScene(380, 240, coroutineContext = clock.context) {
            AuroraTestTheme(appearance = HbAppearance.Light) {
                AuroraWorkEvent(event, expanded, current = false, connectedBelow = false, touch = false) {
                    expanded = !expanded
                }
            }
        }
        try {
            clock.frame(scene)
            val header = assertNotNull(scene.nodes().firstOrNull {
                it.config.getOrNull(SemanticsProperties.StateDescription) == "Свёрнуто"
            }).boundsInRoot
            val collapsedName = assertNotNull(scene.textNode(name))
            val status = assertNotNull(scene.textNode("Готово"))
            assertEquals(32f, header.height)
            assertEquals(collapsedName.boundsInRoot.center.y, status.boundsInRoot.center.y)
            val layout = collapsedName.textLayout()
            assertEquals(1, layout.lineCount)
            assertTrue(layout.getLineEnd(0, visibleEnd = true) < name.length)
            assertEquals("Готово".length, status.textLayout().getLineEnd(0, visibleEnd = true))
            assertEquals(12f, status.boundsInRoot.left - collapsedName.boundsInRoot.right)
            assertEquals(32f, header.right - status.boundsInRoot.right)
            assertNull(scene.textNode(label))

            scene.sendPointerEvent(PointerEventType.Press, header.center, button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, header.center, button = PointerButton.Primary)
            clock.frame(scene)
            assertTrue(expanded)
            val fullName = assertNotNull(scene.textNode(name)).textLayout()
            assertTrue(fullName.lineCount > 1)
            assertFalse(fullName.hasVisualOverflow)
            assertNotNull(scene.textNode(label))
            assertNotNull(scene.textNode(event.details))
        } finally {
            scene.close()
        }
    }

    @Test
    fun thinkingMovesTheIconAndFadesThePreviewAccordingToMotionPreference() {
        val event = pencilTimelineFixture().steps.first().events.last()
        val title = checkNotNull(event.title)
        for (motion in listOf(HbMotionPreference.Full, HbMotionPreference.Reduced)) {
            val clock = AuroraHoverTestClock()
            var expanded by mutableStateOf(false)
            val scene = ImageComposeScene(380, 240, coroutineContext = clock.context) {
                AuroraTestTheme(appearance = HbAppearance.Light, motion = motion) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        AuroraWorkEvent(event, expanded, current = false, connectedBelow = false, touch = false) {
                            expanded = !expanded
                        }
                    }
                }
            }
            try {
                clock.frame(scene)
                val preview = assertNotNull(scene.textNode(event.preview)).boundsInRoot
                val collapsedPreview = scene.darkestRed(preview, clock.nanoTime)
                val collapsedIcon = scene.thinkingIconTop(clock.nanoTime)
                expanded = true
                clock.frame(scene)
                val initial = scene.textDarkestRed(event.detailText, clock.nanoTime)
                val initialPreview = scene.darkestRed(preview, clock.nanoTime)
                val initialIcon = scene.thinkingIconTop(clock.nanoTime)
                repeat(3) { clock.advance(scene, 16) }
                val duringPreview = scene.darkestRed(preview, clock.nanoTime)
                clock.advance(scene, 16)
                val duringIcon = scene.thinkingIconTop(clock.nanoTime)
                clock.advance(scene, 48)
                val during = scene.textDarkestRed(event.detailText, clock.nanoTime)
                clock.advance(scene, 220)
                val settled = scene.textDarkestRed(event.detailText, clock.nanoTime)
                val settledIcon = scene.thinkingIconTop(clock.nanoTime)
                scene.assertExpandedTitleCentered(title)
                if (motion == HbMotionPreference.Full) {
                    assertTrue(
                        initial > during && during > settled,
                        "Detail text intensity: $initial, $during, $settled",
                    )
                    assertEquals(collapsedPreview, initialPreview)
                    assertTrue(
                        duringPreview in (initialPreview + 1)..220,
                        "Preview text intensity: $initialPreview, $duringPreview",
                    )
                    assertEquals(collapsedIcon, initialIcon)
                    assertTrue(
                        initialIcon > duringIcon && duringIcon > settledIcon,
                        "Icon position: $initialIcon, $duringIcon, $settledIcon",
                    )
                } else {
                    assertEquals(settled, initial)
                    assertEquals(settledIcon, initialIcon)
                    assertNull(scene.textNode(event.preview))
                }
            } finally {
                scene.close()
            }
        }
    }

    private fun ImageComposeScene.assertExpandedTitleCentered(title: String) {
        val header = assertNotNull(nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.StateDescription)?.endsWith("Развёрнуто") == true
        }).boundsInRoot
        val titleBounds = assertNotNull(textNode(title)).boundsInRoot
        assertEquals(header.center.y, titleBounds.center.y, 1f)
    }

    private fun ImageComposeScene.textDarkestRed(text: String, nanoTime: Long): Int =
        darkestRed(assertNotNull(textNode(text)).boundsInRoot, nanoTime)

    private fun ImageComposeScene.thinkingIconTop(nanoTime: Long): Int = render(nanoTime).use { image ->
        Bitmap.makeFromImage(image).use { bitmap ->
            (0 until 48).first { y -> (44 until 60).any { x -> (bitmap.getColor(x, y) ushr 16 and 0xff) < 64 } }
        }
    }

    private fun ImageComposeScene.darkestRed(bounds: Rect, nanoTime: Long): Int = render(nanoTime).use { image ->
        Bitmap.makeFromImage(image).use { bitmap ->
            var darkest = 255
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    darkest = minOf(darkest, (bitmap.getColor(x, y) ushr 16) and 0xff)
                }
            }
            darkest
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> =
        semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }

    private fun ImageComposeScene.textNode(text: String): SemanticsNode? =
        nodes().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        check(config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) == true)
        return results.single()
    }
}
