package io.github.aequicor.heartbeat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import io.github.aequicor.heartbeat.designsystem.components.HbAppName
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbProvidePlatformTextStyle
import io.github.aequicor.heartbeat.designsystem.theme.HbProvideWindowCaption
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import java.awt.AWTEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

private val CaptionHeight = 32.dp

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun EdgeToEdgeHeartbeatWindow(
    state: WindowState,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val decorations = remember { JBR.getWindowDecorations() }
    var platformTextStyle by remember { mutableStateOf<PlatformTextStyle?>(null) }
    SwingWindow(
        onCloseRequest = onClose,
        state = state,
        title = HbAppName,
        init = { platformTextStyle = configureWindowsText(it) },
    ) {
        DesktopWindowIcons(window)
        HbProvidePlatformTextStyle(platformTextStyle) {
            val fullscreen = state.placement == WindowPlacement.Fullscreen
            if (decorations == null || fullscreen) {
                content()
            } else {
                EdgeToEdgeCaption(window, decorations, content)
            }
        }
    }
}

@Composable
private fun EdgeToEdgeCaption(
    window: ComposeWindow,
    decorations: WindowDecorations,
    content: @Composable () -> Unit,
) {
    val controls = remember(window) { CaptionControls(window, decorations.createCustomTitleBar()) }
    var insets by remember(controls) { mutableStateOf(0.dp to 0.dp) }
    DisposableEffect(controls) {
        val unregister = controls.install(decorations) { left, right ->
            insets = left.dp to right.dp
        }
        onDispose(unregister)
    }
    val caption = remember(insets, controls) {
        HbWindowCaption(insets.second, CaptionHeight, insets.first, controls::updateClientArea)
    }
    HbProvideWindowCaption(caption) {
        Box(Modifier.fillMaxSize().background(HbAuroraColors.Canvas)) { content() }
    }
}

private class CaptionControls(
    private val window: ComposeWindow,
    private val titleBar: WindowDecorations.CustomTitleBar,
) {
    private val clientAreas = mutableMapOf<Any, Rect>()

    fun updateClientArea(key: Any, bounds: Rect?) {
        if (bounds == null) clientAreas.remove(key) else clientAreas[key] = bounds
    }

    fun install(decorations: WindowDecorations, onInsets: (left: Float, right: Float) -> Unit): () -> Unit {
        titleBar.height = CaptionHeight.value
        titleBar.putProperty("controls.dark", false)
        decorations.setCustomTitleBar(window, titleBar)
        fun readInsets() = onInsets(titleBar.leftInset, titleBar.rightInset)

        val opened = object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) = readInsets()
        }
        val resized = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) = readInsets()
            override fun componentShown(event: ComponentEvent) = readInsets()
        }
        val densityChanged = PropertyChangeListener { readInsets() }
        val mouse = AWTEventListener { event ->
            if (event is MouseEvent && event.id != MouseEvent.MOUSE_EXITED) routeMouse(event)
        }
        val toolkit = Toolkit.getDefaultToolkit()
        window.addWindowListener(opened)
        window.addComponentListener(resized)
        window.addPropertyChangeListener("graphicsConfiguration", densityChanged)
        toolkit.addAWTEventListener(mouse, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
        readInsets()
        var installed = true
        EventQueue.invokeLater { if (installed) readInsets() }

        return {
            installed = false
            toolkit.removeAWTEventListener(mouse)
            window.removePropertyChangeListener("graphicsConfiguration", densityChanged)
            window.removeComponentListener(resized)
            window.removeWindowListener(opened)
            decorations.setCustomTitleBar(window, null)
        }
    }

    private fun routeMouse(event: MouseEvent) {
        val source = event.source as? Component ?: return
        if (source != window && SwingUtilities.getWindowAncestor(source) != window) return
        val point = SwingUtilities.convertPoint(source, event.point, window.contentPane)
        if (point.y >= 0 && point.y < CaptionHeight.value) {
            val scale = window.graphicsConfiguration.defaultTransform
            val position = Offset((point.x * scale.scaleX).toFloat(), (point.y * scale.scaleY).toFloat())
            titleBar.forceHitTest(clientAreas.values.any { it.contains(position) })
        }
    }
}
