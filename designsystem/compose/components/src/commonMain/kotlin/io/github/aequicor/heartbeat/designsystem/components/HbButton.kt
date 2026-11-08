@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import kotlinx.coroutines.delay

/**
 * Primary action button built on foundation, without a Material dependency. The clickable node
 * exposes the [Role.Button] semantics, so accessibility services announce an action button, not a
 * clickable container.
 *
 * States: default uses the primary action pair, hover and press use their own semantic action
 * colors, [busy] prevents duplicate activation immediately, announces the busy state through the
 * localized [busyLabel] provided by the owner, and shows a delayed spinner so a short operation does
 * not flicker; disabled keeps the container muted and dims the label. The focus outline is drawn
 * outside the button with the documented gap, so it stays a separate layer over the surface. The
 * component has no error state: a failed action belongs to the screen that owns the operation.
 */
@Composable
fun HbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyLabel: String? = null,
) {
    val colors = HbTheme.colors
    val interactive = enabled && !busy
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    var busyIndicatorVisible by remember { mutableStateOf(false) }

    LaunchedEffect(busy) {
        if (busy) {
            delay(HbMotions.Default.busyIndicatorDelayMs.toLong())
            busyIndicatorVisible = true
        } else {
            busyIndicatorVisible = false
        }
    }

    // A disabled action leaves the primary pair entirely: a dimmed copy of a light dark-theme
    // fill would still look enabled, so the muted state uses the subtle surface and the
    // disabled text role.
    val containerColor = when {
        !enabled -> colors.surface.subtle
        pressed -> colors.action.primaryPressedBackground
        hovered -> colors.action.primaryHoverBackground
        else -> colors.action.primaryBackground
    }
    val contentColor = if (enabled) colors.action.primaryForeground else colors.text.disabled

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = enabled && focused, shape = HbShapes.Small, color = colors.focus)
            .semantics {
                if (busy && busyLabel != null) stateDescription = busyLabel
            }
            .clip(HbShapes.Small)
            .background(containerColor)
            .heightIn(min = HbSizes.controlHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = HbSpacing.M, vertical = HbSpacing.S),
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        ) {
            if (busyIndicatorVisible) {
                BusyIndicator(
                    color = contentColor,
                    modifier = Modifier.size(BUSY_INDICATOR_SIZE),
                )
            }
            BasicText(
                text = text,
                color = { contentColor },
                style = HbTypography.Body,
            )
        }
    }
}

/** Small indeterminate spinner with a static form under reduced motion. */
@Composable
private fun BusyIndicator(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = HbTheme.motion == HbMotionPreference.Reduced
    val rotation = if (reducedMotion) {
        remember { mutableStateOf(BusyStaticAngle) }
    } else {
        val transition = rememberInfiniteTransition(label = "busyIndicator")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = HbMotions.Default.deliberateTransitionMs,
                    easing = LinearEasing,
                ),
            ),
            label = "busyRotation",
        )
    }

    Canvas(modifier = modifier) {
        rotate(rotation.value) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = BusySweepDegrees,
                useCenter = false,
                style = Stroke(width = BUSY_STROKE_WIDTH.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

private val BUSY_INDICATOR_SIZE = 16.dp

private val BUSY_STROKE_WIDTH = 2.dp

private const val BusySweepDegrees = 270f

private const val BusyStaticAngle = 0f
