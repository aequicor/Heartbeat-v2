package io.github.aequicor.heartbeat.features.aistudio.ui

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import java.awt.GraphicsEnvironment
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.runners.model.MultipleFailureException

/**
 * Runs with HEARTBEAT_LIVE_DESKTOP_BENCH=1 on an unattended macOS desktop.
 * It opens a real window and uses native input.
 * Select only freshCompletedHistory in a fresh test worker to measure the first long completed reply.
 * Select longCodeNativeWheelAndDragSelectionFrames to measure the input phases after opening the code.
 * Set HEARTBEAT_LIVE_DESKTOP_GATE=1 to enforce timing budgets. For the fresh case, also set
 * HEARTBEAT_LIVE_DESKTOP_READY_BASELINE_MS to the measured readiness of the paired baseline opening.
 * Set HEARTBEAT_LIVE_DESKTOP_FULL_MOTION=1 for the same scenario with full animations.
 * Re-run the Gradle test task after changing these variables.
 */
class AuroraLiveDesktopBenchmarkTest {
    @Test
    fun longCodeNativeWheelAndDragSelectionFrames() {
        requireNativeBenchmark()
        AuroraNativeBenchmarkWindow().use { host ->
            val input = AuroraNativeInput(host.window)
            input.awaitFocus()
            println("Native long code window ${host.environment()}")
            verifyNativeCode(host, input, measureGestures = true)
        }
    }

    @Test
    fun freshCompletedHistory() {
        requireNativeBenchmark()
        AuroraNativeBenchmarkWindow().use { host ->
            val input = AuroraNativeInput(host.window)
            input.awaitFocus()
            println("Fresh native window ${host.environment()}")
            host.open(typicalFixture())
            // Let the real window finish drawing a typical conversation before opening the first large report.
            Thread.sleep(WindowSettleMs)
            val opening = host.open(mixedFixture())
            reportFreshOpening(opening)
            input.screenshot("native-mixed-completed")
            val checks = listOf(
                { verifyMixedHistoryPixels(host, input) },
                { verifyMixedQuote(host, input) },
                { verifyNativeCode(host, input) },
                { verifyCompactCodeCopy(host, input) },
                { verifyFreshBudget(opening) },
            )
            MultipleFailureException.assertEmpty(checks.mapNotNull { runCatching(it).exceptionOrNull() })
        }
    }

    @Test
    fun mixedReplyNativeFramesAndCodeInput() = runBlocking {
        requireNativeBenchmark()
        val raw = mixedFixture()
        val reply = Reply("mixed-history", "", text = raw.source, status = ReplyStatus.Completed)
        val fixture = raw.copy(document = requireNotNull(reply.historyContent).document())
        AuroraNativeBenchmarkWindow().use { host ->
            val input = AuroraNativeInput(host.window)
            input.awaitFocus()
            println("Native window ${host.environment()}")
            val size = nativeEdt { host.window.contentPane.size }
            val warmups = List(WarmupOpenings) { host.open(fixture) }
            val samples = List(MeasuredOpenings) {
                host.open(fixture).also { assertEquals(size, nativeEdt { host.window.contentPane.size }) }
            }
            val wheel = host.profileWheel(input)
            verifyNativeCode(host, input)
            reportNativeTimings(warmups, samples, wheel)
        }
    }
}

private fun requireNativeBenchmark() {
    assumeTrue(System.getenv("HEARTBEAT_LIVE_DESKTOP_BENCH") == "1")
    assumeTrue(System.getProperty("os.name").startsWith("Mac") && !GraphicsEnvironment.isHeadless())
}

private const val WindowSettleMs = 250L
private const val WarmupOpenings = 3
private const val MeasuredOpenings = 20
private const val RenderBudgetMs = 16.67
private const val FreshRenderBudgetMs = 16.7
private const val ReadyBudgetMs = 100.0
private const val WheelBudgetMs = 33.33

private fun reportFreshOpening(opening: NativeOpeningTiming) {
    println(
        "Fresh completed history, JVM=${ProcessHandle.current().pid()}, visible=${opening.visibleMs}ms, " +
            "all10=${opening.readyMs}ms, maxRender=${opening.maxMs}ms, maxGap=${opening.maxGapMs}ms",
    )
    println("Fresh first callback ${opening.frames.first()}")
    println("Fresh worst callback ${opening.frames.maxBy { it.renderMs }}")
    opening.frames.forEach { println("Fresh completed history callback $it") }
}

private fun verifyFreshBudget(opening: NativeOpeningTiming) {
    if (System.getenv("HEARTBEAT_LIVE_DESKTOP_GATE") != "1") return
    val baseline = System.getenv("HEARTBEAT_LIVE_DESKTOP_READY_BASELINE_MS")?.toDoubleOrNull()
    requireNotNull(baseline?.takeIf { it.isFinite() && it > 0.0 }) {
        "Set HEARTBEAT_LIVE_DESKTOP_READY_BASELINE_MS to the paired baseline readiness in milliseconds"
    }
    val ready = maxOf(opening.visibleMs, opening.readyMs)
    assertTrue(
        opening.maxMs <= FreshRenderBudgetMs && ready <= baseline,
        "Fresh longest callback ${opening.maxMs} ms (budget $FreshRenderBudgetMs ms), " +
            "report readiness $ready ms (paired baseline $baseline ms)",
    )
}

private fun reportNativeTimings(
    warmups: List<NativeOpeningTiming>,
    samples: List<NativeOpeningTiming>,
    wheel: NativeWheelTiming,
) {
    warmups.forEach {
        println("Prepared publish warmup maxRender=${it.maxMs}ms, visible=${it.visibleMs}ms, all10=${it.readyMs}ms")
    }
    val visible = report("Visible tail", samples.map { it.visibleMs })
    val allCode = report("All ten code blocks", samples.map { it.readyMs })
    val render = report("Per-opening longest render callback", samples.map { it.maxMs })
    report("Per-opening longest callback completion gap", samples.map { it.maxGapMs })
    println("Native history: ${wheel.initial} -> ${wheel.final}")
    val wheelRender = report("Wheel-phase render callbacks", wheel.frames)
    val wheelLatency = report("Native wheel dispatch to completed callback", wheel.responses)
    if (System.getenv("HEARTBEAT_LIVE_DESKTOP_GATE") == "1") {
        assertTrue(render <= RenderBudgetMs, "Cold render p95 $render ms exceeds $RenderBudgetMs ms")
        assertTrue(visible <= ReadyBudgetMs && allCode <= ReadyBudgetMs, "Content readiness exceeds budget")
        assertTrue(wheelRender <= RenderBudgetMs && wheelLatency <= WheelBudgetMs, "Native wheel exceeds budget")
    }
}

private fun report(label: String, values: List<Double>): Double {
    val p95 = values.sorted()[ceil(values.size * 0.95).toInt() - 1]
    println("$label (ms), n=${values.size}, p95=$p95, worst=${values.max()}")
    return p95
}

private fun mixedFixture(): NativeReplyFixture {
    val source = buildString {
        repeat(150) { index ->
            appendLine("## Check ${index + 1}")
            appendLine()
            appendLine("A **formatted** paragraph shows the result of the current check and describes the next step.")
            appendLine()
            if (index % 15 == 0) {
                appendLine("~~~kotlin")
                repeat(18) { appendLine("val result$it = checkStatus($index)") }
                appendLine("~~~")
                appendLine()
                appendLine("| Item | State |")
                appendLine("| --- | :---: |")
                appendLine("| Result | Ready |")
                appendLine()
            }
        }
        appendLine("> $MixedQuote")
        appendLine()
        appendLine(MixedTail)
    }
    return NativeReplyFixture(
        source = source,
        visible = MixedTail,
        codeMarkers = (0 until 150 step 15).map { "val result0 = checkStatus($it)" },
    )
}

private const val DeepCodeMarker = "= checkStatus(135)"
private const val MixedTail = "Mixed-tail-marker"
private const val MaximumDeepWheelSteps = 70
private const val WheelSettleMs = 45L

private fun verifyMixedHistoryPixels(host: AuroraNativeBenchmarkWindow, input: AuroraNativeInput) {
    val initial = host.historyPosition()
    val older = host.moveHistory(input, notches = 1)
    scrollUntil(input, rotation = 3 * older, description = "The older code must enter the viewport") {
        input.textIsVisible(DeepCodeMarker)
    }
    assertTrue(host.historyPosition() != initial, "The real wheel must move the history")
    input.screenshot("native-mixed-wheel-depth")
    input.requireTextInk(DeepCodeMarker)

    scrollUntil(input, rotation = -3 * older, description = "The wheel must return to the original reading position") {
        host.historyPosition() == initial && input.textIsVisible(MixedTail)
    }
    input.screenshot("native-mixed-wheel-return")
    input.requireTextInk(MixedTail)
}

private fun scrollUntil(input: AuroraNativeInput, rotation: Int, description: String, ready: () -> Boolean) {
    repeat(MaximumDeepWheelSteps) {
        if (ready()) return
        input.wheel(rotation)
        Thread.sleep(WheelSettleMs)
    }
    assertTrue(ready(), description)
}

private const val MixedQuote = "A stable quote checks native selection after the report opens."

private fun verifyMixedQuote(host: AuroraNativeBenchmarkWindow, input: AuroraNativeInput) {
    val start = input.cursor(MixedQuote, 0)
    val end = input.cursor(MixedQuote, MixedQuote.length)
    withNativeClipboard(setOf(MixedQuote)) {
        val inkBeforeDrag = input.selectionInk(MixedQuote)
        input.drag(start, end)
        nativeUntil("The quote must be visibly selected before scrolling the history") {
            input.selectionInk(MixedQuote) > inkBeforeDrag + 30
        }
        val before = host.historyPosition()
        host.moveHistory(input, notches = 1)
        assertTrue(host.historyPosition() != before, "The native wheel must advance the completed report")
        input.copySelection()
        nativeUntil("Cmd+C must preserve the selected quote across native history scroll") {
            clipText() == MixedQuote
        }
    }
}

private fun typicalFixture() = NativeReplyFixture(
    source = """
        An ordinary **completed** reply.

        ~~~kotlin
        val typical = checkStatus(0)
        ~~~

        | Item | State |
        | --- | --- |
        | Typical | Ready |

        Typical-tail-marker
    """.trimIndent(),
    visible = "Typical-tail-marker",
    codeMarkers = listOf("val typical = checkStatus(0)"),
)
