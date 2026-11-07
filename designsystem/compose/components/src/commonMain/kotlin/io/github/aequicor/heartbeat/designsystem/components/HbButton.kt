package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Primary action button built on foundation, without a Material dependency. The clickable node
 * exposes the [Role.Button] semantics, so accessibility services announce an action button, not a
 * clickable container.
 *
 * States: default uses the primary action pair, hover and press use their own semantic action
 * colors, [busy] prevents duplicate activation immediately and announces the busy state through
 * [busyLabel], disabled keeps the container muted and dims the label. The focus outline is drawn
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
    busyLabel: String = DefaultBusyLabel,
) {
    val colors = HbTheme.colors
    val interactive = enabled && !busy
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }

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
                if (busy) stateDescription = busyLabel
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
        BasicText(
            text = text,
            color = { contentColor },
            style = HbTypography.Body,
        )
    }
}

private const val DefaultBusyLabel = "Выполняется"
