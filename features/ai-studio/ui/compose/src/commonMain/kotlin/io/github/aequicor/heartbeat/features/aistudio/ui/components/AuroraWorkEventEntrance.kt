package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AuroraWorkEventEntrance(private val progress: () -> Float) {

    val previousProgress: Float get() = segmentProgress(0)

    val gapProgress: Float get() = segmentProgress(1)

    val markerProgress: Float get() = segmentProgress(2)

    val ready: Boolean get() = progress() >= 1f

    private fun segmentProgress(index: Int): Float = (progress() * ConnectorSegments - index).coerceIn(0f, 1f)
}

@Composable
internal fun rememberWorkEventEntrance(animateOnEntry: Boolean, queue: Mutex): AuroraWorkEventEntrance? {
    val animate = remember { animateOnEntry }
    if (!animate || HbTheme.motion == HbMotionPreference.Reduced) return null
    val line = remember { Animatable(0f) }
    LaunchedEffect(line, queue) {
        queue.withLock { line.animateTo(1f, tween(WorkLineMillis, easing = LinearEasing)) }
    }
    return remember(line) { AuroraWorkEventEntrance { line.value } }
}

private const val ConnectorSegments = 3
private const val WorkLineMillis = 64
