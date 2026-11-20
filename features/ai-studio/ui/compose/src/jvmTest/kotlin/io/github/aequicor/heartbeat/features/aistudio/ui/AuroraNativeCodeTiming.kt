package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.ui.awt.ComposeWindow
import java.awt.AWTEvent
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.util.ArrayDeque
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.test.assertTrue

internal enum class NativeCodeGesture(val mask: Long, val eventId: Int) {
    Wheel(AWTEvent.MOUSE_WHEEL_EVENT_MASK, MouseEvent.MOUSE_WHEEL),
    Drag(AWTEvent.MOUSE_MOTION_EVENT_MASK, MouseEvent.MOUSE_DRAGGED),
}

/** Collects only real EDT input and Skiko callbacks during one robot gesture. */
internal class NativeCodeGestureRecorder(private val window: ComposeWindow, private val gesture: NativeCodeGesture) :
    AWTEventListener {
    private val pending = ArrayDeque<Long>()
    private val callbacks = mutableListOf<Double>()
    private val inputCallbacks = mutableListOf<Double>()
    private val responses = mutableListOf<Double>()
    private var events = 0

    override fun eventDispatched(event: AWTEvent) {
        if (event is MouseEvent && event.id == gesture.eventId &&
            SwingUtilities.getWindowAncestor(event.component) == window
        ) {
            pending.addLast(System.nanoTime())
            events++
        }
    }

    fun onRender(start: Long, end: Long) {
        if (events == 0) return
        val renderMs = (end - start) / 1_000_000.0
        callbacks += renderMs
        if (pending.isEmpty()) return
        inputCallbacks += renderMs
        while (pending.isNotEmpty()) responses += (end - pending.removeFirst()) / 1_000_000.0
    }

    fun timing() = NativeCodeGestureTiming(
        gesture, events, pending.size, callbacks.toList(), inputCallbacks.toList(), responses.toList(),
    )
}

internal data class NativeCodeGestureTiming(
    val gesture: NativeCodeGesture,
    val events: Int,
    val unmatchedEvents: Int,
    val callbacks: List<Double>,
    val inputCallbacks: List<Double>,
    val responses: List<Double>,
) {
    fun report() {
        assertTrue(events > 1 && inputCallbacks.isNotEmpty(), "Native ${gesture.name} must drive rendered frames")
        println(
            "Native long code ${gesture.name}: events=$events unmatched=$unmatchedEvents " +
                "all callbacks=${callbacks.size} p95=${callbacks.p95()}ms worst=${callbacks.max()}ms; " +
                "input callbacks=${inputCallbacks.size} p95=${inputCallbacks.p95()}ms " +
                "worst=${inputCallbacks.max()}ms; " +
                "dispatch-to-render=${responses.size} p95=${responses.p95()}ms worst=${responses.max()}ms",
        )
    }

    private fun List<Double>.p95() = sorted()[ceil(size * 0.95).toInt() - 1]
}
