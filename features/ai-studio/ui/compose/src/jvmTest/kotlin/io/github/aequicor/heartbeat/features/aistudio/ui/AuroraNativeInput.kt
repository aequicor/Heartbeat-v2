@file:OptIn(
    androidx.compose.runtime.tooling.ComposeToolingApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlin.test.assertTrue

internal class AuroraNativeInput(private val window: ComposeWindow) {
    private val robot = Robot()

    fun awaitFocus() {
        if (nativeEdt { window.isFocused }) return
        val origin = nativeEdt { window.contentPane.locationOnScreen }
        robot.mouseMove(origin.x + 8, origin.y + 24)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        nativeUntil("The benchmark window must have native focus") { nativeEdt { window.isFocused } }
    }

    fun control(label: String): Rectangle = nativeEdt {
        val node = window.nativeNodes().first {
            label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
        val bounds = node.boundsInWindow
        val from = screen(Offset(bounds.left, bounds.top))
        val to = screen(Offset(bounds.right, bounds.bottom))
        Rectangle(from.x, from.y, to.x - from.x, to.y - from.y)
    }

    fun textIsVisible(marker: String): Boolean = nativeEdt { visibleTextBounds(marker) != null }

    fun requireTextInk(marker: String) {
        val dark = captureTextPixels(marker).count {
            (it and 0x00FF0000) < 0x00900000 && (it and 0x0000FF00) < 0x00009000
        }
        println("Native text ink for $marker, dark pixels=$dark")
        assertTrue(dark >= MinimumTextInkPixels, "Placed text has no visible glyphs: $marker")
    }

    fun selectionInk(marker: String): Int = captureTextPixels(marker).count { pixel ->
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        blue > 180 && blue - red > 35 && blue - green > 15
    }

    private fun captureTextPixels(marker: String): IntArray {
        val region = nativeEdt {
            val bounds = requireNotNull(visibleTextBounds(marker)) { "Text is outside the native viewport: $marker" }
            val topLeft = screen(bounds.topLeft)
            val bottomRight = screen(bounds.bottomRight)
            Rectangle(topLeft.x, topLeft.y, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
                .intersection(Rectangle(window.contentPane.locationOnScreen, window.contentPane.size))
        }
        requireFocus()
        val image = robot.createScreenCapture(region)
        return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    }

    private fun visibleTextBounds(marker: String): Rect? =
        window.nativeNodes()
            .filter { it.hasText(marker) }
            .filter { it.boundsInWindow.height >= MinimumGlyphHeight && window.intersectsContent(it.boundsInWindow) }
            .mapNotNull { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                val layout = layouts.firstOrNull { marker in it.layoutInput.text.text } ?: return@mapNotNull null
                val start = layout.layoutInput.text.text.indexOf(marker)
                val bounds = layout.getPathForRange(start, start + marker.length).getBounds()
                bounds.translate(node.positionInWindow).intersect(node.boundsInWindow)
            }
            .firstOrNull { it.width > 0 && it.height >= MinimumGlyphHeight && window.intersectsContent(it) }

    fun cursor(marker: String, offset: Int): Point = nativeEdt {
        val node = requireNotNull(window.findText(marker))
        val layouts = mutableListOf<TextLayoutResult>()
        requireNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(layouts)
        val layout = layouts.first { marker in it.layoutInput.text.text }
        val caret = layout.getCursorRect(layout.layoutInput.text.text.indexOf(marker) + offset)
        screen(node.positionInWindow + caret.center + Offset(0.5f, 0f))
    }

    private fun screen(at: Offset): Point {
        val origin = window.contentPane.locationOnScreen
        val scale = window.graphicsConfiguration.defaultTransform
        return Point(origin.x + (at.x / scale.scaleX).roundToInt(), origin.y + (at.y / scale.scaleY).roundToInt())
    }

    fun scrollPosition(key: SemanticsPropertyKey<ScrollAxisRange>) =
        nativeEdt { window.nativeNodes().firstNotNullOf { node -> node.config.getOrNull(key)?.value?.invoke() } }

    fun screenshot(name: String) {
        Thread.sleep(120)
        requireFocus()
        val bounds = nativeEdt { Rectangle(window.contentPane.locationOnScreen, window.contentPane.size) }
        val directory = File("build/screenshots").apply { mkdirs() }
        ImageIO.write(robot.createScreenCapture(bounds), "png", File(directory, "$name.png"))
    }

    fun moveTo(at: Point) {
        requireFocus()
        robot.mouseMove(at.x, at.y)
    }

    fun click(bounds: Rectangle) {
        moveTo(Point(bounds.centerX.toInt(), bounds.centerY.toInt()))
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    }

    fun drag(from: Point, to: Point) {
        moveTo(from)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        try {
            for (step in 1..14) {
                requireFocus()
                robot.mouseMove(from.x + (to.x - from.x) * step / 14, from.y + (to.y - from.y) * step / 14)
                Thread.sleep(12)
            }
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        }
    }

    fun wheel(rotation: Int) {
        requireFocus()
        robot.mouseWheel(rotation)
    }

    fun copySelection() {
        requireFocus()
        robot.keyPress(KeyEvent.VK_META)
        try {
            robot.keyPress(KeyEvent.VK_C)
            robot.keyRelease(KeyEvent.VK_C)
        } finally {
            robot.keyRelease(KeyEvent.VK_META)
        }
    }

    private fun requireFocus() = check(nativeEdt { window.isFocused }) {
        "Native input stopped because focus left the test window"
    }
}

private const val MinimumTextInkPixels = 20
private const val MinimumGlyphHeight = 20f

internal fun ComposeWindow.nativeNodes(): Sequence<SemanticsNode> =
    semanticsOwners.asSequence().flatMap { placedNodes(it.rootSemanticsNode) }

private fun placedNodes(node: SemanticsNode): Sequence<SemanticsNode> =
    if (node.layoutInfo.isPlaced) sequenceOf(node) + node.children.asSequence().flatMap(::placedNodes)
    else emptySequence()

internal fun ComposeWindow.intersectsContent(bounds: Rect): Boolean {
    val scale = graphicsConfiguration.defaultTransform
    return bounds.width > 0f && bounds.height > 0f && bounds.right > 0f && bounds.bottom > 0f &&
        bounds.left < contentPane.width * scale.scaleX && bounds.top < contentPane.height * scale.scaleY
}

internal fun ComposeWindow.findText(label: String): SemanticsNode? =
    nativeNodes().firstOrNull { it.hasText(label) }

internal fun SemanticsNode.hasText(label: String): Boolean =
    config.getOrNull(SemanticsProperties.Text).orEmpty().any { label in it.text }

internal fun nativeUntil(reason: String, predicate: () -> Boolean) {
    val deadline = System.nanoTime() + 6_000_000_000L
    while (System.nanoTime() < deadline) {
        if (predicate()) return
        Thread.sleep(12)
    }
    assertTrue(predicate(), reason)
}

internal fun <T> nativeEdt(action: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return action()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait { result = runCatching(action) }
    return requireNotNull(result).getOrThrow()
}
