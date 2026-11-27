package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraReferenceJump
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

internal class AuroraReferenceHighlight(val jump: AuroraReferenceJump) {

    private val opacity = Animatable(0f)
    private val messagePositioned = CompletableDeferred<Unit>()
    private val targetPositioned = CompletableDeferred<Unit>()

    val alpha: Float get() = opacity.value

    suspend fun show(reducedMotion: Boolean, positionMessage: suspend () -> Unit) {
        positionMessage()
        messagePositioned.complete(Unit)
        targetPositioned.await()
        opacity.snapTo(1f)
        delay(HighlightMillis)
        if (reducedMotion) opacity.snapTo(0f) else opacity.animateTo(0f, tween(FadeMillis))
    }

    suspend fun revealTarget(positionTarget: suspend () -> Unit) {
        messagePositioned.await()
        if (targetPositioned.isCompleted) return
        positionTarget()
        targetPositioned.complete(Unit)
    }
}

private const val HighlightMillis = 1_200L
private const val FadeMillis = 300
