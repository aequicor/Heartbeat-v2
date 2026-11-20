@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

internal open class AuroraWorkSceneTest {
    protected val clock = AuroraHoverTestClock()

    protected fun ImageComposeScene.capture(name: String) {
        val directory = File("build/screenshots").apply { mkdirs() }
        render(clock.nanoTime).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use {
                File(directory, "$name.png").writeBytes(it.bytes)
            }
        }
    }

    protected fun ImageComposeScene.moveTo(point: Offset) {
        sendPointerEvent(PointerEventType.Move, point)
        clock.frame(this)
    }

    protected fun ImageComposeScene.click(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        clock.frame(this)
    }

    protected fun ImageComposeScene.hasText(text: String): Boolean =
        textNode(text) != null

    protected fun ImageComposeScene.textNode(text: String): SemanticsNode? =
        node { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == text } }

    protected fun ImageComposeScene.node(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(matches) }

    private fun SemanticsNode.find(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (matches(this)) this else children.firstNotNullOfOrNull { it.find(matches) }
}
