package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Primary action button built on foundation, without a Material dependency. The clickable node
 * exposes the [Role.Button] semantics, so accessibility services announce an action button, not a
 * clickable container.
 *
 * States: default uses the primary action pair, [busy] prevents duplicate activation immediately
 * while the container and the label stay readable, disabled keeps the container muted and dims the
 * label, pressed lightens the container, focused draws the design system focus outline in
 * [io.github.aequicor.heartbeat.designsystem.theme.HbColors.focus]. The component has no error state:
 * a failed action belongs to the screen that owns the operation.
 */
@Composable
fun HbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val colors = HbTheme.colors
    val interactive = enabled && !busy
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }

    // A disabled action leaves the primary pair entirely: a dimmed copy of a light dark-theme
    // fill would still look enabled, so the muted state uses the subtle surface and the
    // disabled text role.
    val containerColor = if (enabled) colors.action.primaryBackground else colors.surface.subtle
    val contentColor = if (enabled) colors.action.primaryForeground else colors.text.disabled

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(HbShapes.Small)
            .background(containerColor)
            .then(
                if (enabled && pressed) {
                    Modifier.background(
                        colors.action.primaryForeground.copy(alpha = HbEffects.PressedLayer),
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (enabled && focused) {
                    Modifier.border(
                        width = HbComponentStates.focusedBorderWidth,
                        color = colors.focus,
                        shape = HbShapes.Small,
                    )
                } else {
                    Modifier
                },
            )
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
