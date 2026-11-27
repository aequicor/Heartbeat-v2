package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import io.github.aequicor.heartbeat.features.aistudio.ui.components.addMacAttachmentMagnificationListener
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import javax.swing.RootPaneContainer

internal actual fun Modifier.imageEditorPlatformZoom(onZoom: (Float, Offset) -> Unit): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestZoom by rememberUpdatedState(onZoom)
    val density = LocalDensity.current.density
    DisposableEffect(density) {
        val target = if (System.getProperty("os.name").startsWith("Mac") && !GraphicsEnvironment.isHeadless()) {
            (KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? RootPaneContainer)?.rootPane
        } else null
        val remove = target?.let {
            addMacAttachmentMagnificationListener(it) { factor, point ->
                val layout = coordinates?.takeIf { coordinate -> coordinate.isAttached } ?: return@addMacAttachmentMagnificationListener
                val anchor = layout.windowToLocal(Offset(point.x * density, point.y * density))
                if (anchor.x in 0f..<layout.size.width.toFloat() && anchor.y in 0f..<layout.size.height.toFloat()) {
                    latestZoom(factor, anchor)
                }
            }
        }
        onDispose { remove?.invoke() }
    }
    onGloballyPositioned { coordinates = it }
}
