package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class AuroraWheelDeadlineTest {
    @Test
    fun aPulseArrivingAtTheIdleDeadlineRemainsAvailableExactlyOnce() = runTest(IdleFrameClock) {
        val wheel = Channel<Int>(Channel.UNLIMITED)
        launch(start = CoroutineStart.UNDISPATCHED) {
            delay(IdleWaitMillis)
            assertTrue(wheel.trySend(WheelDistance).isSuccess)
        }

        val received = wheel.awaitPatchedWheelDelta(IdleWaitMillis)
        assertEquals(listOf(WheelDistance), listOfNotNull(received, wheel.tryReceive().getOrNull()))
        assertTrue(wheel.tryReceive().isFailure)
    }

    @Test
    fun anExpiredIntervalLeavesAReadyPulseForTheNextGesture() = runTest(IdleFrameClock) {
        val wheel = Channel<Int>(Channel.UNLIMITED)
        assertTrue(wheel.trySend(WheelDistance).isSuccess)

        assertNull(wheel.awaitPatchedWheelDelta(0))
        assertEquals(WheelDistance, wheel.awaitPatchedWheelDelta(IdleWaitMillis))
    }
}

// The patched Foundation keeps its original Kotlin module metadata.
private suspend fun Channel<Int>.awaitPatchedWheelDelta(timeoutMillis: Long): Int? =
    suspendCoroutineUninterceptedOrReturn { continuation ->
        Class.forName("androidx.compose.foundation.gestures.MouseWheelScrollingLogicKt")
            .getDeclaredMethod("awaitWheelDelta", Channel::class.java, java.lang.Long.TYPE, Continuation::class.java)
            .invoke(null, this, timeoutMillis, continuation)
    }

private object IdleFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = awaitCancellation()
}

private const val IdleWaitMillis = 26L
private const val WheelDistance = 60
