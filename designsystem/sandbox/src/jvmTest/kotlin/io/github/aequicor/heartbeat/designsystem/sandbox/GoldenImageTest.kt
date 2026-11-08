package io.github.aequicor.heartbeat.designsystem.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals

/** Diagnostics of the golden-image check (HB-DS-040): a pixel share is printed once as a percent. */
class GoldenImageTest {

    @Test
    fun pixelShareIsFormattedAsAPercentageOnce() {
        assertEquals("0.05%", formatPercent(0.0005))
        assertEquals("1.00%", formatPercent(0.01))
        assertEquals("100.00%", formatPercent(1.0))
    }
}
