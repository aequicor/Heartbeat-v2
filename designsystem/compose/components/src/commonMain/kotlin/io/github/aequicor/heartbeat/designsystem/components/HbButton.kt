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
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Primary action button built on foundation, without a Material dependency. The clickable node
 * exposes the [Role.Button] semantics, so accessibility services announce an action button, not a
 * clickable container.
 *
 * States: default uses the primary container, disabled keeps the container muted and dims the
 * label, pressed lightens the container, focused draws a border in the container content color.
 * The component has no error state: a failed action belongs to the screen that owns the operation.
 */
@Composable
fun HbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HbThemeDefaults.LocalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }

    val containerColor = if (enabled) {
        colors.primary
    } else {
        colors.onSurface.copy(alpha = HbComponentStates.DISABLED_CONTAINER_ALPHA)
    }
    val contentColor = if (enabled) {
        colors.onPrimary
    } else {
        colors.onSurface.copy(alpha = HbComponentStates.DISABLED_CONTENT_ALPHA)
    }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(HbShapes.Medium)
            .background(containerColor)
            .then(
                if (enabled && pressed) {
                    Modifier.background(
                        colors.onPrimary.copy(alpha = HbComponentStates.PRESSED_OVERLAY_ALPHA),
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (enabled && focused) {
                    Modifier.border(
                        width = HbComponentStates.focusedBorderWidth,
                        color = colors.onPrimary,
                        shape = HbShapes.Medium,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
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
