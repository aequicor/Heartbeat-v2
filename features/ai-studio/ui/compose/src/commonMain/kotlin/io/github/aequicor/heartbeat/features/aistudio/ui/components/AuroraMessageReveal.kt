package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlinx.coroutines.delay

private const val MessageHoverDelayMillis = 500L

internal data class AuroraMessageReveal(
    val interactive: Boolean,
    val drawn: Boolean,
    val opacity: State<Float>,
)

@Composable
internal fun OpenMessageMenuOnBadgeHover(
    hovered: Boolean,
    expanded: Boolean,
    interactive: Boolean,
    onOpen: () -> Unit,
) {
    var handled by remember { mutableStateOf(false) }
    SideEffect(hovered, expanded, onOpen) {
        when {
            expanded -> Unit
            !hovered -> handled = false
            interactive && !handled -> {
                handled = true
                onOpen()
            }
        }
    }
}

@Composable
internal fun CloseMessageMenuOnHoverExit(
    expanded: Boolean, overMenu: Boolean, overBadge: Boolean, overBubble: Boolean, onClose: () -> Unit,
) {
    if (expanded && !overMenu && !overBadge) {
        LaunchedEffect(overBubble, onClose) {
            // Allow crossing from the badge to the popup before its pointer enter arrives.
            if (!overBubble) delay(HbMotions.Default.smallStateChangeMs.toLong())
            onClose()
        }
    }
}

@Composable
internal fun rememberMessageActionBadge(
    hovered: Boolean, overBubble: Boolean, pinned: Boolean, expanded: Boolean, touch: Boolean,
): AuroraMessageReveal {
    val admitted by rememberBadgeAdmission(hovered, overBubble, expanded)
    val available = pinned || hovered && admitted
    val visible = available && (!expanded || touch)
    val animated = !touch && !pinned && HbTheme.motion != HbMotionPreference.Reduced
    val opacity = animateFloatAsState(
        if (visible) 1f else 0f,
        if (animated) tween(HbMotions.Default.ordinaryTransitionMs, easing = FastOutSlowInEasing) else snap(),
        label = "Message action badge",
    )
    val fading by remember(opacity) { derivedStateOf { opacity.value > 0f } }
    return AuroraMessageReveal(available || expanded, visible || fading, opacity)
}

@Composable
private fun rememberBadgeAdmission(hovered: Boolean, overBubble: Boolean, expanded: Boolean): State<Boolean> {
    val admission = remember { mutableStateOf(false) }
    var admitted by admission
    LaunchedEffect(hovered, overBubble, expanded) {
        when {
            expanded -> Unit
            !hovered -> admitted = false
            overBubble && !admitted -> {
                delay(MessageHoverDelayMillis)
                admitted = true
            }
        }
    }
    return admission
}

/** Hover has a dwell time. An existing reveal can reverse its fade while still on screen. */
@Composable
internal fun rememberAuroraMessageReveal(
    hovered: Boolean,
    pinned: Boolean,
    dwellOnTarget: Boolean = hovered,
    exitDelayMillis: Long = 0L,
    holdHover: Boolean = false,
): AuroraMessageReveal {
    val opacity = remember { Animatable(0f) }
    val fadingOrVisible by remember(opacity) { derivedStateOf { opacity.value > 0f } }
    var ready by remember { mutableStateOf(false) }
    if (dwellOnTarget) {
        LaunchedEffect(Unit) {
            delay(MessageHoverDelayMillis)
            ready = true
        }
    } else if (ready) {
        SideEffect { ready = false }
    }

    val heldAfterHover = rememberHoverExitRetention(hovered, fadingOrVisible, exitDelayMillis, holdHover)
    val visible = pinned || heldAfterHover || hovered && (fadingOrVisible || dwellOnTarget && ready)
    val reduceMotion = HbTheme.motion == HbMotionPreference.Reduced
    if (visible || fadingOrVisible) {
        LaunchedEffect(visible, pinned, reduceMotion) {
            val target = if (visible) 1f else 0f
            if (pinned || reduceMotion) {
                opacity.snapTo(target)
            } else {
                opacity.animateTo(target, tween(HbMotions.Default.ordinaryTransitionMs, easing = FastOutSlowInEasing))
            }
        }
    }
    return AuroraMessageReveal(visible, visible || fadingOrVisible, opacity.asState())
}

@Composable
private fun rememberHoverExitRetention(
    hovered: Boolean,
    shown: Boolean,
    delayMillis: Long,
    holdHover: Boolean,
): Boolean {
    if (delayMillis <= 0L) return false

    var retained by remember { mutableStateOf(false) }
    if (hovered && shown && !retained) SideEffect { retained = true }
    if (!hovered && retained && !holdHover) {
        LaunchedEffect(delayMillis) {
            delay(delayMillis)
            retained = false
        }
    }
    return retained
}
