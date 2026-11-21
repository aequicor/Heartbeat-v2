package io.github.aequicor.heartbeat.features.aistudio.presentation.mock

/** Synthetic tokens of up to three Unicode code points; not a provider's model tokenizer. */
internal class MockTokenStream(private val source: String) {
    private var offset = 0
    private var elapsedMillis = 0L

    data class Batch(val text: String, val delayMillis: Long, val complete: Boolean)

    fun next(): Batch? {
        if (offset == source.length) return null
        val rate = Rates[((elapsedMillis / RateWindowMillis) % Rates.size).toInt()]
        val requested = (rate * BatchWindowMillis / MillisPerSecond).coerceAtLeast(1)
        val start = offset
        var emitted = 0
        repeat(requested) {
            if (offset < source.length) {
                repeat(TokenCodePoints) {
                    if (offset < source.length) {
                        val surrogate = source[offset].isHighSurrogate() &&
                            source.getOrNull(offset + 1)?.isLowSurrogate() == true
                        offset += if (surrogate) 2 else 1
                    }
                }
                emitted++
            }
        }
        val delay = (emitted * MillisPerSecond + rate - 1L) / rate
        elapsedMillis += delay
        return Batch(source.substring(start, offset), delay, offset == source.length)
    }

    private companion object {
        const val TokenCodePoints = 3
        const val BatchWindowMillis = 32
        const val MillisPerSecond = 1_000
        const val RateWindowMillis = 1_000L
        @Suppress("MagicNumber") // Deliberate deterministic rate profile for the local demonstration.
        val Rates = listOf(10, 45, 150, 75, 20, 110)
    }
}
