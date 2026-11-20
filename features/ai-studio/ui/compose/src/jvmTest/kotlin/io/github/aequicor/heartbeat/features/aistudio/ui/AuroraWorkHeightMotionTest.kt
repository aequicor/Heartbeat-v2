@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkAccess
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraWorkHeightMotion
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuroraWorkHeightMotionTest {
    @Test
    fun expandedWorkEntersAtItsMeasuredHeightAndAnimatesLaterChanges() {
        val clock = TestCoroutineScheduler()
        val access = AuroraWorkAccess(startedLive = false)
        var height by mutableIntStateOf(40)
        val reported = mutableListOf<Int>()
        val scene = ImageComposeScene(
            width = 320,
            height = 360,
            density = Density(1f),
            coroutineContext = StandardTestDispatcher(clock),
        ) {
            AuroraTestTheme(motion = HbMotionPreference.Full) {
                Box(auroraWorkHeightMotion(access, false, {}, onFrame = { shown, _ -> reported.add(shown) })) {
                    Box(Modifier.height(height.dp))
                }
            }
        }
        fun frame() {
            clock.advanceTimeBy(16)
            clock.runCurrent()
            scene.render(clock.currentTime * 1_000_000L).close()
            clock.runCurrent()
        }
        try {
            repeat(4) { frame() }
            assertEquals(40, access.displayedHistoryHeight)
            assertTrue(reported.isEmpty(), "Initial placement should not start a resize")

            height = 80
            frame()
            assertEquals(40, access.displayedHistoryHeight)
            repeat(24) { frame() }
            assertTrue(reported.any { it in 41..79 })
            assertEquals(80, access.displayedHistoryHeight)

            reported.clear()
            height = 20
            frame()
            assertEquals(80, access.displayedHistoryHeight)
            repeat(24) { frame() }
            assertTrue(reported.any { it in 21..79 })
            assertEquals(20, access.displayedHistoryHeight)
        } finally {
            scene.close()
        }
    }
}
