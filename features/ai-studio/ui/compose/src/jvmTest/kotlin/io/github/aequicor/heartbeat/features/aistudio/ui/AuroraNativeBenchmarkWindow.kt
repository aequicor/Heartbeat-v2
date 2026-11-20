@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraChatHost
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraHistory
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseWheelEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

internal class AuroraNativeBenchmarkWindow : AutoCloseable {
    private val motion = if (System.getenv("HEARTBEAT_LIVE_DESKTOP_FULL_MOTION") == "1") {
        HbMotionPreference.Full
    } else HbMotionPreference.Reduced
    private val toolkit = Toolkit.getDefaultToolkit()
    private val reading = mutableStateOf(WindowReply())
    private val live = MutableStateFlow(LiveText())
    private var identity = 0
    private var cold: NativeOpening? = null
    private var codeGesture: NativeCodeGestureRecorder? = null
    private var recordWheel = false
    private val wheelFrames = ConcurrentLinkedQueue<Double>()
    private val wheelResponse = ConcurrentLinkedQueue<Double>()
    private val wheelEvents = ConcurrentLinkedQueue<Long>()
    val window = nativeEdt {
        ComposeWindow().apply {
            title = "Heartbeat native rendering benchmark"
            setSize(WindowWidth, WindowHeight)
            setLocationRelativeTo(null)
            val host = this
            setContent {
                val displayed = reading.value
                val generation = historyGeneration(displayed.id)
                AuroraTestTheme(motion = motion) {
                    AuroraChatHost(generation) {
                        AuroraHistory(
                            displayed.state, live, displayed.list, false, {}, PaddingValues(24.dp),
                            Modifier.fillMaxSize().background(HbAuroraColors.Canvas).semantics {
                                testTag = generation
                            },
                            viewportHeight = host.contentPane.height.dp,
                        )
                    }
                }
            }
            isVisible = true
            toFront()
            requestFocus()
            val layer = requireNotNull(findNativeSkia(this))
            val delegate = requireNotNull(layer.renderDelegate)
            layer.renderDelegate = SkikoRenderDelegate { canvas, width, height, logicalNanos ->
                val start = System.nanoTime()
                delegate.onRender(canvas, width, height, logicalNanos)
                val end = System.nanoTime()
                codeGesture?.onRender(start, end)
                if (recordWheel) {
                    wheelFrames += toMs(end - start)
                    while (true) {
                        val event = wheelEvents.poll() ?: break
                        wheelResponse += toMs(end - event)
                    }
                }
                cold?.record(this, end, end - start)
            }
        }
    }
    private val wheelListener = AWTEventListener { event ->
        if (recordWheel && event is MouseWheelEvent && SwingUtilities.getWindowAncestor(event.component) == window) {
            wheelEvents += System.nanoTime()
        }
    }

    init {
        toolkit.addAWTEventListener(wheelListener, AWTEvent.MOUSE_WHEEL_EVENT_MASK)
    }

    fun environment() = nativeEdt {
        val screen = window.graphicsConfiguration
        "OS=${System.getProperty("os.version")} Java=${System.getProperty("java.runtime.version")} " +
            "backend=${window.renderApi} motion=$motion refresh=${screen.device.displayMode.refreshRate} " +
            "scale=${screen.defaultTransform.scaleX} content=${window.contentPane.size}"
    }

    fun open(fixture: NativeReplyFixture): NativeOpeningTiming {
        if (fixture.document != null) resetHistory()
        val result = nativeEdt {
            val generation = ++identity
            val id = historyGeneration(generation)
            val next = NativeOpening(fixture)
            cold = next
            next.start = System.nanoTime()
            val document = fixture.document
            val state = if (document == null) {
                val reply = Reply(id, "", text = fixture.source, status = ReplyStatus.Completed)
                AiStudioScreenState(messages = listOf(reply, User("prompt", "", "Show the latest checks.")))
            } else {
                live.value = LiveText(id, fixture.source, document)
                AiStudioScreenState(live = Reply(id, ""), messages = older())
            }
            reading.value = WindowReply(generation, state)
            next
        }
        nativeUntil("The native frame must show the visible and every code marker") { result.done.get() != 0L }
        nativeEdt { cold = null }
        return result.timing()
    }

    private fun resetHistory() {
        val sentinel = nativeEdt {
            live.value = LiveText()
            reading.value = WindowReply(id = ++identity)
            historyGeneration(identity)
        }
        nativeUntil("The native history must finish resetting before prepared publish") {
            nativeEdt { window.nativeNodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == sentinel } }
        }
    }

    fun moveHistory(input: AuroraNativeInput, notches: Int): Int {
        val origin = nativeEdt { window.contentPane.locationOnScreen }
        // The gutter belongs to the outer history even when a nested code viewport fills the reading column.
        input.moveTo(Point(origin.x + 18, origin.y + 335))
        return listOf(1, -1).first { direction ->
            val before = historyPosition()
            input.wheel(notches * direction)
            Thread.sleep(350)
            historyPosition() != before
        }
    }

    fun profileWheel(input: AuroraNativeInput): NativeWheelTiming {
        val direction = moveHistory(input, notches = 8)
        val initial = historyPosition()
        nativeEdt { recordWheel = true }
        repeat(60) {
            input.wheel(direction)
            Thread.sleep(42)
        }
        nativeUntil("Native wheel events must lead to rendered frames") { wheelResponse.size >= 55 }
        nativeEdt { recordWheel = false }
        val final = historyPosition()
        assertTrue(wheelFrames.size >= 55)
        assertTrue(final != initial, "The measured wheel phase must traverse history")
        return NativeWheelTiming(initial, final, wheelFrames.toList(), wheelResponse.toList())
    }

    fun measureCodeGesture(gesture: NativeCodeGesture, action: () -> Unit): NativeCodeGestureTiming {
        val recorder = NativeCodeGestureRecorder(window, gesture)
        nativeEdt {
            check(codeGesture == null && cold == null)
            codeGesture = recorder
            toolkit.addAWTEventListener(recorder, gesture.mask)
        }
        try {
            action()
        } finally {
            nativeEdt {
                toolkit.removeAWTEventListener(recorder)
                codeGesture = null
            }
        }
        return nativeEdt { recorder.timing() }
    }

    fun historyPosition() = nativeEdt {
        val list = reading.value.list
        list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
    }

    override fun close() {
        toolkit.removeAWTEventListener(wheelListener)
        nativeEdt { window.dispose() }
    }
}

private const val WindowWidth = 980
private const val WindowHeight = 700

internal data class NativeReplyFixture(
    val source: String,
    val visible: String,
    val codeMarkers: List<String>,
    val document: ReplyDocument? = null,
)

internal data class NativeOpeningTiming(
    val visibleMs: Double,
    val readyMs: Double,
    val maxMs: Double,
    val maxGapMs: Double,
    val frames: List<NativeOpeningFrame>,
)

internal data class NativeOpeningFrame(
    val sincePublishMs: Double,
    val renderMs: Double,
    val callbackGapMs: Double,
    val tailVisible: Boolean,
    val placedCodeBlocks: Int,
)

internal data class NativeWheelTiming(
    val initial: Pair<Int, Int>,
    val final: Pair<Int, Int>,
    val frames: List<Double>,
    val responses: List<Double>,
)

private fun toMs(nanos: Long): Double = nanos / 1_000_000.0

private class WindowReply(
    val id: Int = 0,
    val state: AiStudioScreenState = AiStudioScreenState(),
    val list: LazyListState = LazyListState(),
)

private fun historyGeneration(id: Int) = "native-history-$id"

private fun older() = List(30) { User("old-$it", "", "Older message $it") }

private fun findNativeSkia(component: Component): SkiaLayer? =
    if (component is SkiaLayer) component
    else (component as? Container)?.components?.firstNotNullOfOrNull(::findNativeSkia)

private class NativeOpening(private val fixture: NativeReplyFixture) {
    var start = 0L
    val done = AtomicLong()
    private var visibleMs = 0.0
    private var readyMs = 0.0
    private val frames = mutableListOf<NativeOpeningFrame>()
    private var lastCompleted = 0L

    fun timing() = NativeOpeningTiming(
        visibleMs, readyMs, frames.maxOf { it.renderMs }, frames.maxOf { it.callbackGapMs }, frames.toList(),
    )

    fun record(window: ComposeWindow, ended: Long, cpu: Long) {
        if (done.get() != 0L) return
        val callbackGap = toMs(ended - (if (lastCompleted == 0L) start else lastCompleted))
        lastCompleted = ended
        val all = window.nativeNodes().toList()
        val tailVisible = all.any { it.hasText(fixture.visible) && window.intersectsContent(it.boundsInWindow) }
        val codeCount = fixture.codeMarkers.count { label -> all.any { it.hasText(label) } }
        frames += NativeOpeningFrame(toMs(ended - start), toMs(cpu), callbackGap, tailVisible, codeCount)
        if (tailVisible && visibleMs == 0.0) {
            visibleMs = toMs(ended - start)
        }
        if (visibleMs > 0.0 && codeCount == fixture.codeMarkers.size) {
            readyMs = toMs(ended - start)
            done.set(ended)
        }
    }
}
