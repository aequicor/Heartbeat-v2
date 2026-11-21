@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.presentation.mock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockTokenStreamTest {
    @Test
    fun batchesVaryWithinTenToOneHundredFiftySyntheticTokensPerSecond() {
        val source = "abc".repeat(2_000)
        val stream = MockTokenStream(source)
        val rates = mutableSetOf<Double>()
        var received = ""
        var elapsed = 0L
        var firstSecondTokens = 0
        while (true) {
            val batch = stream.next() ?: break
            val tokens = batch.text.length / 3
            val rate = tokens * 1000.0 / batch.delayMillis
            assertTrue(rate in 10.0..150.0)
            assertTrue(batch.text.length <= 12, "A batch must not dump an entire reasoning paragraph")
            rates += rate
            elapsed += batch.delayMillis
            if (elapsed <= 1_000) firstSecondTokens += tokens
            received += batch.text
            assertEquals(received.length == source.length, batch.complete)
        }
        assertEquals(10, firstSecondTokens)
        assertTrue(rates.size >= 4)
        assertTrue(rates.max() > 140)
        assertEquals(source, received)
        assertNull(stream.next())
    }

    @Test
    fun unicodeTextKeepsItsOrderAndNeverSplitsASurrogatePair() {
        val source = "Проверка 🚀🧩 и **разметка**\nследующей строки".repeat(20)
        val stream = MockTokenStream(source)
        val received = buildString {
            while (true) {
                val batch = stream.next() ?: break
                assertFalse(batch.text.first().isLowSurrogate())
                assertFalse(batch.text.last().isHighSurrogate())
                append(batch.text)
            }
        }
        assertEquals(source, received)
    }

    @Test
    fun emptyAndCompletedStreamsHaveNoExtraBatchAndReplayIsDeterministic() {
        assertNull(MockTokenStream("").next())
        val one = assertNotNull(MockTokenStream("x").next())
        assertEquals("x", one.text)
        assertTrue(one.complete)
        val first = MockTokenStream("abc".repeat(100))
        val second = MockTokenStream("abc".repeat(100))
        repeat(100) { assertEquals(first.next(), second.next()) }
    }
}
