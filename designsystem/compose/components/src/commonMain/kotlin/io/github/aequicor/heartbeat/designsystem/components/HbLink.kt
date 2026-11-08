package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Text link that opens a resource (HB-DS-015). Links navigate or open; buttons perform actions. The
 * underline is always present in reading content, and the accessible role is a button until the
 * platform exposes a dedicated link role. The effective hit area keeps the density minimum target
 * while the label keeps its compact visual size.
 */
@Composable
fun HbLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .clip(HbShapes.Small)
            .heightIn(min = HbSizes.minimumTarget)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
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
}
