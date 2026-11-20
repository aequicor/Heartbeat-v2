@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistoryScrollbar
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraScrollbarViewport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat

class AuroraHistoryScrollbarTest {
    private val clock = AuroraHoverTestClock()

    @Test
    fun hoverAndGrabAreLimitedToTheThumbAndWheelPassesThroughWithoutHighlight() {
        val list = LazyListState()
        withScene(list) { scene ->
            scene.checkWidth(4, screenshot = "scrollbar-rest")
            val thumb = Offset(234f, 16f)
            scene.move(Offset(234f, 80f))
            scene.checkWidth(4)
            scene.drag(Offset(234f, 80f), Offset(234f, 126f))
            assertFalse(list.canScrollBackward)

            scene.move(thumb.copy(x = 227f))
            scene.checkWidth(4)
            scene.drag(thumb.copy(x = 227f), thumb.copy(y = 120f))
            assertFalse(list.canScrollBackward)

            scene.move(thumb)
            scene.checkWidth(6, screenshot = "scrollbar-hover")
            scene.sendPointerEvent(PointerEventType.Scroll, thumb, scrollDelta = Offset(0f, 1f))
            scene.checkWidth(4)
            clock.advance(scene, 200)
            clock.advance(scene, 200)
            assertTrue(list.canScrollBackward)
            scene.checkWidth(4)
            list.requestScrollToItem(0)
            clock.frame(scene)
            scene.move(thumb.copy(x = 227f))
            scene.move(thumb)
            scene.checkWidth(6)
        }
    }

    @Test
    fun reverseThumbRetainsTheGripOutsideTheRailAndReachesBothEnds() {
        val list = LazyListState()
        withScene(list, reverse = true) { scene ->
            val grip = Offset(234f, 147f)
            scene.move(grip)
            scene.checkWidth(6)
            scene.sendPointerEvent(PointerEventType.Press, grip, button = PointerButton.Primary)
            scene.checkWidth(6)
            assertFalse(list.canScrollBackward)
            assertEquals(128, scene.thumbTop())

            val outside = Offset(220f, 2f)
            scene.move(outside)
            scene.checkWidth(6, screenshot = "scrollbar-drag")
            assertEquals(0, scene.thumbTop())
            scene.sendPointerEvent(PointerEventType.Release, outside, button = PointerButton.Primary)
            scene.checkWidth(4)
            assertFalse(list.canScrollForward)
            assertEquals(0, scene.thumbTop())

            scene.tap(Key.Tab)
            scene.tap(Key.Tab)
            scene.checkWidth(6)
            scene.tap(Key.MoveEnd)
            assertFalse(list.canScrollBackward)
            assertEquals(128, scene.thumbTop())
        }
    }

    @Test
    fun viewportIsContinuousAcrossRowsOfUnevenHeightAndIgnoresOverscroll() {
        val list = LazyListState()
        withScene(list) { scene ->
            var previous = assertNotNull(list.auroraScrollbarViewport())
            var crossings = 0
            repeat(400) {
                list.dispatchRawDelta(1f)
                val current = assertNotNull(list.auroraScrollbarViewport())
                assertTrue(current.progress >= previous.progress)
                assertTrue(current.progress - previous.progress <= 0.001f)
                if (current.start.toInt() > previous.start.toInt()) crossings++
                previous = current
            }
            assertTrue(crossings > 4)

            list.requestScrollToItem(0)
            clock.frame(scene)
            assertTrue(list.dispatchRawDelta(-100f) == 0f)
            assertEquals(0f, assertNotNull(list.auroraScrollbarViewport()).progress)
        }
    }

    @Test
    fun noThumbIsDrawnWhenTheListFits() {
        withScene(LazyListState(), rows = listOf(22.dp)) { scene ->
            clock.frame(scene)
            assertEquals(-1, scene.thumbTop())
        }
    }

    @Test
    fun touchScrollingKeepsTheThumbThin() {
        val list = LazyListState()
        withScene(list, touch = true) { scene ->
            val start = Offset(120f, 110f)
            val end = Offset(120f, 60f)
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Move, end, type = PointerType.Touch)
            clock.frame(scene)
            assertTrue(list.isScrollInProgress)
            scene.checkWidth(4, screenshot = "scrollbar-touch-scroll")
            scene.sendPointerEvent(PointerEventType.Release, end, type = PointerType.Touch)
        }
    }

    @Test
    fun firstThumbMovementAtTheChatTailDoesNotSkipTheComposerInset() {
        val list = LazyListState()
        val padding = PaddingValues(top = 8.dp, bottom = 76.dp)
        withScene(list, reverse = true, rows = unevenRows(10), padding = padding) { scene ->
            val grip = Offset(234f, 146f)
            scene.move(grip)
            scene.sendPointerEvent(PointerEventType.Press, grip, button = PointerButton.Primary)
            assertFalse(list.canScrollBackward)
            scene.move(grip.copy(y = 144f))
            assertEquals(0, list.firstVisibleItemIndex)
            assertTrue(list.firstVisibleItemScrollOffset in 1..6)
            scene.sendPointerEvent(PointerEventType.Release, grip.copy(y = 144f), button = PointerButton.Primary)
        }
    }

    @Test
    fun draggingAcrossUnevenItemsDoesNotReverseTheListPosition() {
        val list = LazyListState()
        withScene(list, rows = unevenRows(10)) { scene ->
            val start = Offset(234f, 12f)
            scene.move(start)
            scene.sendPointerEvent(PointerEventType.Press, start, button = PointerButton.Primary)
            var previous = 0.0
            for (y in 13..158) {
                scene.move(start.copy(y = y.toFloat()))
                val progress = assertNotNull(list.auroraScrollbarViewport()).start
                assertTrue(progress >= previous, "Pointer y=$y, viewport moved from $previous back to $progress")
                previous = progress
            }
            assertFalse(list.canScrollForward)
            for (y in 157 downTo 11) {
                scene.move(start.copy(y = y.toFloat()))
                val progress = assertNotNull(list.auroraScrollbarViewport()).start
                assertTrue(progress <= previous, "Pointer y=$y, viewport moved from $previous forward to $progress")
                previous = progress
            }
            scene.sendPointerEvent(PointerEventType.Release, start.copy(y = 11f), button = PointerButton.Primary)
            assertFalse(list.canScrollBackward)
        }
    }

    @Test
    fun theThumbStopsWithTheChatAndCanImmediatelyDragBackFromItsTop() {
        val list = LazyListState()
        val rows = listOf(237.dp) + List(9) { 22.dp }
        withScene(list, reverse = true, rows = rows) { scene ->
            val grip = Offset(234f, 146f)
            scene.move(grip)
            scene.sendPointerEvent(PointerEventType.Press, grip, button = PointerButton.Primary)
            val clampedAt = assertNotNull((145 downTo 10).firstOrNull { y ->
                scene.move(grip.copy(y = y.toFloat()))
                !list.canScrollForward
            })
            val first = list.firstVisibleItemIndex
            val offset = list.firstVisibleItemScrollOffset
            assertEquals(0, scene.thumbTop())
            scene.checkWidth(6, screenshot = "scrollbar-edge-drag")
            scene.move(grip.copy(y = clampedAt - 4f))
            assertEquals(0, scene.thumbTop())
            assertEquals(first, list.firstVisibleItemIndex)
            assertEquals(offset, list.firstVisibleItemScrollOffset)

            val reversePoint = grip.copy(y = clampedAt + 4f)
            scene.move(reversePoint)
            assertTrue(list.canScrollForward)
            scene.sendPointerEvent(PointerEventType.Release, reversePoint, button = PointerButton.Primary)
        }
    }

    private fun withScene(
        list: LazyListState,
        reverse: Boolean = false,
        rows: List<Dp> = unevenRows(80),
        touch: Boolean = false,
        padding: PaddingValues = PaddingValues(top = 8.dp, bottom = 12.dp),
        action: (ImageComposeScene) -> Unit,
    ) {
        val scene = scene(list, reverse, rows, touch, padding)
        try {
            clock.frame(scene)
            action(scene)
        } finally {
            scene.close()
        }
    }

    private fun scene(list: LazyListState, reverse: Boolean, rows: List<Dp>, touch: Boolean, padding: PaddingValues) =
        ImageComposeScene(240, 160, coroutineContext = clock.context) {
            HbDesktopFocusIndication(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = list,
                        reverseLayout = reverse,
                        contentPadding = padding,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(rows.size) { index ->
                            Box(Modifier.height(rows[index]))
                        }
                    }
                    AuroraHistoryScrollbar(
                        list, "Прокрутка истории", Modifier.align(Alignment.CenterEnd), reverse, touch,
                    )
                }
            }
        }

    private fun unevenRows(count: Int): List<Dp> = List(count) { index ->
        when {
            index == count - 1 -> 237.dp
            index % 2 == 0 -> 22.dp
            else -> 96.dp
        }
    }

    private fun ImageComposeScene.move(position: Offset) {
        sendPointerEvent(PointerEventType.Move, position)
        clock.frame(this)
    }

    private fun ImageComposeScene.tap(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        clock.frame(this)
    }

    private fun ImageComposeScene.drag(from: Offset, to: Offset) {
        sendPointerEvent(PointerEventType.Press, from, button = PointerButton.Primary)
        move(to)
        sendPointerEvent(PointerEventType.Release, to, button = PointerButton.Primary)
        clock.frame(this)
    }

    private fun ImageComposeScene.checkWidth(width: Int, screenshot: String? = null) {
        clock.frame(this)
        render(clock.nanoTime).use { image ->
            Bitmap.makeFromImage(image).use { bitmap ->
                val top = (0 until 160).first { bitmap.getColor(234, it) != -1 }
                val bottom = (159 downTo 0).first { bitmap.getColor(234, it) != -1 }
                assertEquals(width, (228 until 240).count { bitmap.getColor(it, (top + bottom) / 2) != -1 })
            }
            if (screenshot != null) {
                val directory = File("build/screenshots").apply { mkdirs() }
                image.encodeToData(EncodedImageFormat.PNG)?.use { encoded ->
                    File(directory, "$screenshot.png").writeBytes(encoded.bytes)
                }
            }
        }
    }

    private fun ImageComposeScene.thumbTop() = render(clock.nanoTime).use { image ->
        Bitmap.makeFromImage(image).use { bitmap -> (0 until 160).indexOfFirst { bitmap.getColor(234, it) != -1 } }
    }
}
