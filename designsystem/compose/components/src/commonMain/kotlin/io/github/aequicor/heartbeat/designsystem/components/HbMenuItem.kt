package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Action row of a menu (HB-DS-019). The row performs an action, and [selected] reports a checked
 * choice without replacing the action. [hint] carries a shortcut or a secondary value and is not
 * the only place the action is named.
 */
@Composable
fun HbMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    hint: String? = null,
) {
    val colors = HbThemeTokens.colors
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (enabled) colors.text.primary else colors.text.primary.copy(HbEffects.DisabledContent)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.controlHeight)
            .clip(HbShapes.Small)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { contentColor },
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            BasicText(
                text = hint,
                style = HbTypography.Metadata,
                color = { colors.text.secondary },
            )
        }
    }
}

