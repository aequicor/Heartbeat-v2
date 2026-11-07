package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextDecoration
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Text link that opens a resource (HB-DS-015). Links navigate or open; buttons perform actions. The
 * underline is always present in reading content, and the accessible role is a button until the
 * platform exposes a dedicated link role.
 */
@Composable
fun HbLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HbThemeDefaults.LocalColors.current
    var focused by remember { mutableStateOf(false) }

    BasicText(
        text = text,
        modifier = modifier
            .clip(HbShapes.Small)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = HbSpacing.Xxs),
        style = HbTypography.Label.copy(textDecoration = TextDecoration.Underline),
        color = {
            if (enabled) {
                colors.accent.foreground
            } else {
                colors.text.primary.copy(HbEffects.DisabledContent)
            }
        },
    )
}
