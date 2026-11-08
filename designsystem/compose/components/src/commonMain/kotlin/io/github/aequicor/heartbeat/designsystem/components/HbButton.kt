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
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.hb_busy
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Action button built on foundation, without a Material dependency. The clickable node exposes the
 * [Role.Button] semantics, so accessibility services announce an action button, not a clickable
 * container.
 *
 * States: default uses the [variant] pair, hover and press use their own semantic colors, [busy]
 * prevents duplicate activation immediately and announces the busy state, and shows a delayed
 * spinner so a short operation does not flicker; disabled keeps the container muted and dims the
 * label. The spinner occupies a slot that is reserved in every state, so the button keeps its width
 * while busy (HB-DS-015). The busy state description is part of the contract: when the owner does
 * not pass [busyLabel], the localized kit label is announced. The focus outline is drawn outside
 * the button with the documented gap, so it stays a separate layer over the surface. The component
 * has no error state: a failed action belongs to the screen that owns the operation.
 */
@Composable
fun HbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyLabel: String? = null,
    variant: HbButtonVariant = HbButtonVariant.Primary,
) {
    val colors = HbTheme.colors
    val interactive = enabled && !busy
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    var busyIndicatorVisible by remember { mutableStateOf(false) }
    val defaultBusyLabel = stringResource(Res.string.hb_busy)

    LaunchedEffect(busy) {
        if (busy) {
            delay(HbMotions.Default.busyIndicatorDelayMs.toLong())
            busyIndicatorVisible = true
        } else {
            busyIndicatorVisible = false
        }
    }

    val visuals = hbButtonVisuals(
        variant = variant,
        enabled = enabled,
        pressed = pressed,
        hovered = hovered,
        colors = colors,
    )

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = enabled && focused, shape = HbShapes.Small, color = colors.focus)
            .semantics {
                if (busy) stateDescription = busyLabel ?: defaultBusyLabel
            }
            .clip(HbShapes.Small)
            .background(visuals.container)
            .then(
                if (visuals.border != null) {
                    Modifier.border(BUTTON_BORDER_WIDTH, visuals.border, HbShapes.Small)
                } else {
                    Modifier
                },
            )
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
        HbButtonContent(
            text = text,
            contentColor = visuals.content,
            indicatorVisible = busyIndicatorVisible,
        )
    }
}

@Composable
private fun HbButtonContent(
    text: String,
    contentColor: Color,
    indicatorVisible: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        // The slot exists in every state, so the delayed indicator cannot change the width.
        Box(modifier = Modifier.size(BUSY_INDICATOR_SIZE), contentAlignment = Alignment.Center) {
            if (indicatorVisible) {
                BusyIndicator(color = contentColor, modifier = Modifier.matchParentSize())
            }
        }
        BasicText(
            text = text,
            color = { contentColor },
            style = HbTypography.Body,
        )
    }
}

private class HbButtonVisuals(
    val container: Color,
    val content: Color,
    val border: Color?,
)

private fun hbButtonVisuals(
    variant: HbButtonVariant,
    enabled: Boolean,
    pressed: Boolean,
    hovered: Boolean,
    colors: HbColors,
): HbButtonVisuals {
    if (!enabled) return disabledButtonVisuals(variant, colors)
    val stateLayer = when {
        pressed -> HbEffects.PressedLayer
        hovered -> HbEffects.HoverLayer
        else -> 0f
    }
    return when (variant) {
        HbButtonVariant.Primary -> HbButtonVisuals(
            container = when {
                pressed -> colors.action.primaryPressedBackground
                hovered -> colors.action.primaryHoverBackground
                else -> colors.action.primaryBackground
            },
            content = colors.action.primaryForeground,
            border = null,
        )

        HbButtonVariant.Secondary -> HbButtonVisuals(
            container = colors.surface.subtle.stateOver(stateLayer, colors.text.primary),
            content = colors.text.primary,
            border = colors.border.control,
        )

        HbButtonVariant.Ghost -> HbButtonVisuals(
            container = colors.surface.base.stateOver(stateLayer, colors.text.primary),
            content = colors.text.primary,
            border = null,
        )

        HbButtonVariant.Destructive -> HbButtonVisuals(
            container = colors.status.error.foreground.stateOver(stateLayer, colors.surface.base),
            content = colors.surface.base,
            border = null,
        )
    }
}

private fun disabledButtonVisuals(variant: HbButtonVariant, colors: HbColors): HbButtonVisuals =
    when (variant) {
        HbButtonVariant.Ghost -> HbButtonVisuals(
            container = Color.Transparent,
            content = colors.text.disabled,
            border = null,
        )

        HbButtonVariant.Secondary -> HbButtonVisuals(
            container = colors.surface.subtle,
            content = colors.text.disabled,
            border = colors.border.subtle,
        )

        HbButtonVariant.Primary, HbButtonVariant.Destructive -> HbButtonVisuals(
            container = colors.surface.subtle,
            content = colors.text.disabled,
            border = null,
        )
    }

/** Opaque neutral layer of the documented interaction strength over [container]. */
private fun Color.stateOver(amount: Float, foreground: Color): Color = if (amount == 0f) {
    this
} else {
    lerp(this, foreground, amount)
}

/** Small indeterminate spinner with a static form under reduced motion. */
@Composable
private fun BusyIndicator(
    color: Color,
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

private val BUTTON_BORDER_WIDTH = 1.dp

private const val BusySweepDegrees = 270f

private const val BusyStaticAngle = 0f
