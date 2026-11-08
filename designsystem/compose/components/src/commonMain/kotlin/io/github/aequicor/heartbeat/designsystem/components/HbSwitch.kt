@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Switch for an immediate setting (HB-DS-018). The visible [label] is the accessible name; the
 * state is announced by the toggle semantics, not by the thumb position alone.
 *
 * The slow asynchronous change of a setting belongs to the owner of the operation: the switch
 * renders the state it receives and keeps the focus outline visible. The thumb starts at the
 * logical start of the track and travels to the logical end, so the control follows the reading
 * direction of the layout.
 */
@Composable
fun HbSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val reducedMotion = HbTheme.motion == HbMotionPreference.Reduced
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val trackColor = when {
        !enabled -> colors.text.disabled
        checked -> colors.accent.brand
        else -> colors.border.control
    }
    val labelColor = if (enabled) colors.text.primary else colors.text.primary.copy(HbEffects.DisabledContent)
    val thumbOffset by animateDpAsState(
        targetValue = when {
            checked && rightToLeft -> -thumbTravel
            checked -> thumbTravel
            else -> 0.dp
        },
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = HbMotions.Default.ordinaryTransitionMs)
        },
        label = "switchThumb",
    )

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .clip(HbShapes.Small)
            .heightIn(min = HbSizes.minimumTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        HbSwitchTrack(
            trackColor = trackColor,
            thumbColor = colors.surface.base,
            thumbOffset = thumbOffset,
            rightToLeft = rightToLeft,
        )
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { labelColor },
        )
    }
}

private val trackWidth = 36.dp

private val trackHeight = 20.dp

private val thumbSize = 16.dp

private val thumbInset = 2.dp

private val thumbTravel = trackWidth - thumbSize - thumbInset * 2

/** Track and thumb: the thumb travels from the logical start to the logical end. */
@Composable
private fun HbSwitchTrack(
    trackColor: Color,
    thumbColor: Color,
    thumbOffset: Dp,
    rightToLeft: Boolean,
) {
    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .padding(horizontal = thumbInset),
        contentAlignment = if (rightToLeft) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}
