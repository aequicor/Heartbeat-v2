package io.github.aequicor.heartbeat.features.aistudio.ui.components

import java.awt.Point
import java.lang.reflect.Proxy
import javax.swing.JComponent

internal fun addMacAttachmentMagnificationListener(
    component: JComponent,
    onZoom: (factor: Float, point: Point) -> Unit,
): (() -> Unit)? {
    if (!System.getProperty("os.name").startsWith("Mac")) return null

    // The Mac AWT forwards magnification separately from mouse-wheel events.
    // Keep these runtime classes off the shared desktop compile classpath.
    return try {
        val utilities = Class.forName("com.apple.eawt.event.GestureUtilities")
        val listener = Class.forName("com.apple.eawt.event.GestureListener")
        val magnifier = Class.forName("com.apple.eawt.event.MagnificationListener")
        val event = Class.forName("com.apple.eawt.event.MagnificationEvent")
        val delta = event.getMethod("getMagnification")
        val add = utilities.getMethod("addGestureListenerTo", JComponent::class.java, listener)
        val remove = utilities.getMethod("removeGestureListenerFrom", JComponent::class.java, listener)
        val proxy = Proxy.newProxyInstance(magnifier.classLoader, arrayOf(magnifier)) { instance, method, arguments ->
            when (method.name) {
                "magnify" -> {
                    val factor = 1f + (delta.invoke(arguments[0]) as Double).toFloat()
                    if (factor.isFinite() && factor > 0f) {
                        component.mousePosition?.let { point -> onZoom(factor, point) }
                    }
                    null
                }
                "equals" -> instance === arguments[0]
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "Attachment magnification listener"
                else -> null
            }
        }
        add.invoke(null, component, proxy)
        val dispose: () -> Unit = { remove.invoke(null, component, proxy) }
        dispose
    } catch (_: ReflectiveOperationException) {
        null
    }
}
