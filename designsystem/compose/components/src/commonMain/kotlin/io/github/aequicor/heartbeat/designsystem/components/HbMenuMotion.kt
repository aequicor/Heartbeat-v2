package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

private const val BadgeExpansionMillis = 160

/** A desktop menu can replace its badge or fade in at a separate pointer anchor. */
data class HbMenuMotion(
    val replaceTrigger: Boolean,
    val hoverMenu: Boolean = false,
    val onHover: (Boolean) -> Unit = {},
)

internal class HbMenuVisual(val progress: () -> Float) {
    var anchor by mutableStateOf(IntOffset.Zero)
    var entries: List<HbMenuEntry> = emptyList()
    val visible by derivedStateOf { progress() > 0f }
}

@Composable
internal fun rememberHbMenuVisual(expanded: Boolean, motion: HbMenuMotion?): HbMenuVisual? {
    if (motion == null || HbTheme.motion == HbMotionPreference.Reduced) return null
    val animation = if (motion.replaceTrigger) tween<Float>(BadgeExpansionMillis, easing = LinearOutSlowInEasing)
    else tween(HbMotions.Default.ordinaryTransitionMs)
    val progress = animateFloatAsState(
        if (expanded) 1f else 0f, animation, label = "Message menu",
    )
    return remember(progress) { HbMenuVisual { progress.value } }
}

@Composable
internal fun HbMenuPopupVisual(
    visual: HbMenuVisual?,
    motion: HbMenuMotion?,
    expanded: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val input = if (motion?.hoverMenu == true && expanded) {
        DisposableEffect(motion.onHover) { onDispose { motion.onHover(false) } }
        Modifier.trackMenuHover(motion.onHover)
    } else Modifier
    val animation = when {
        visual == null -> Modifier
        motion?.replaceTrigger == true -> Modifier.clipFromBadge(visual)
        else -> Modifier.graphicsLayer { alpha = visual.progress() }
    }
    val accessibility = if (expanded) Modifier else Modifier.semantics { hideFromAccessibility() }
    val items = if (visual == null || motion?.replaceTrigger != true) Modifier else Modifier.graphicsLayer {
        alpha = (visual.progress() * 4f - 3f).coerceIn(0f, 1f)
    }
    Box(input.then(animation).then(accessibility)) { content(items) }
}

private fun Modifier.trackMenuHover(onHover: (Boolean) -> Unit): Modifier = pointerInput(onHover) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.type == PointerType.Mouse }) continue
            when (event.type) {
                PointerEventType.Enter, PointerEventType.Move -> onHover(true)
                PointerEventType.Exit -> onHover(false)
            }
        }
    }
}

private fun Modifier.clipFromBadge(visual: HbMenuVisual): Modifier = drawWithContent {
    val progress = visual.progress()
    if (progress == 1f) {
        drawContent()
    } else {
        val radius = HbAuroraLayout.MessageMenuBadge.toPx() / 2
        val (x, y) = visual.anchor
        val bounds = Rect(
            lerp(x - radius, 0f, progress), lerp(y - radius, 0f, progress),
            lerp(x + radius, size.width, progress), lerp(y + radius, size.height, progress),
        )
        val corner = CornerRadius(lerp(radius, HbAuroraLayout.SmallRadius.toPx(), progress))
        val shape = Path().apply { addRoundRect(RoundRect(bounds, corner)) }
        clipPath(shape) { this@drawWithContent.drawContent() }
    }
}
