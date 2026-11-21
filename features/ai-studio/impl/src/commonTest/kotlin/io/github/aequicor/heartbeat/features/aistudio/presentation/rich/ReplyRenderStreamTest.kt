@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.aequicor.heartbeat.features.aistudio.presentation.rich

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplyRenderStreamTest {
    @Test
    fun firstReadyTextIsImmediateAndBusyInputDoesNotMoveTheDeadline() = runTest {
        val shown = mutableListOf<LiveText>()
        val callbacks = mutableListOf<() -> Unit>()
        val stream = ReplyRenderStream({ milliseconds, callback ->
            callbacks += callback
            backgroundScope.launch { delay(milliseconds); callback() }
        }, shown::add)
        val owner = Any()
        stream.update(owner, "a", "\n\n")
        assertTrue(shown.isEmpty())
        stream.update(owner, "a", "[link](htt")
        assertTrue(shown.isEmpty())
        assertTrue(callbacks.isEmpty())
        stream.update(owner, "a", "[link](https://example.com) ")
        assertEquals(1, shown.size)
        stream.update(owner, "a", shown.last().text + "A")
        runCurrent()
        repeat(6) { index ->
            advanceTimeBy(5)
            stream.update(owner, "a", shown.first().text + "A" + "x".repeat(index + 1))
            runCurrent()
        }
        assertEquals(1, callbacks.size)
        assertEquals(1, shown.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(
            listOf("[link](https://example.com) ", "[link](https://example.com) Axxxxxx"),
            shown.map { it.text },
        )
    }

    @Test
    fun continuousInputKeepsPublishingDuringGeneration() = runTest {
        val shown = mutableListOf<LiveText>()
        val stream = ReplyRenderStream({ milliseconds, callback ->
            backgroundScope.launch { delay(milliseconds); callback() }
        }, shown::add)
        val owner = Any()
        stream.update(owner, "a", "Start")
        repeat(3) { window ->
            repeat(8) { step ->
                stream.update(owner, "a", "Start" + "x".repeat(window * 8 + step + 1))
                runCurrent()
                advanceTimeBy(4)
                runCurrent()
            }
            assertEquals(window + 2, shown.size)
            assertEquals("Start" + "x".repeat((window + 1) * 8), shown.last().text)
        }
    }

    @Test
    fun completionAtFiveMillisFlushesUnfinishedSyntaxAndInvalidatesEvenAnAlreadyQueuedTimer() = runTest {
        val shown = mutableListOf<LiveText>()
        var late: (() -> Unit)? = null
        val stream = ReplyRenderStream({ milliseconds, callback ->
            late = callback
            backgroundScope.launch { delay(milliseconds); callback() }
        }, shown::add)
        val owner = Any()
        stream.update(owner, "a", "First ")
        stream.update(owner, "a", "First more [unfinished")
        runCurrent()
        advanceTimeBy(5)
        val source = "First more [unfinished"
        val final = stream.finish(source)
        assertEquals(5L, testScheduler.currentTime)
        assertEquals(ReplyMarkdownParser().parse(source, final = true), final)
        stream.update(Any(), "b", "Next")
        late?.invoke()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("First ", "Next"), shown.map { it.text })
    }

    @Test
    fun completeOnlyAndEmptyTailsNeverScheduleATimer() = runTest {
        var timers = 0
        val stream = ReplyRenderStream({ _, _ -> timers++; Job() }, {})
        val source = "Whole **answer with unfinished markup"
        assertEquals(ReplyMarkdownParser().parse(source, final = true), stream.finish(source))
        stream.update(Any(), "a", "Plain ")
        stream.update(Any(), "b", "[pending")
        assertEquals(0, timers)
    }
}
