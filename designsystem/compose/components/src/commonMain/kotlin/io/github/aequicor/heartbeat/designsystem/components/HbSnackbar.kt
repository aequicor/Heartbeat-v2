package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Brief confirmation or reversible action (HB-DS-026).
 *
 * The bar carries at most one action and announces itself politely, so the message reaches
 * assistive services without moving the focus. Its lifetime belongs to the owner: a snackbar is
 * never the only record of a consequential failure, and a decision uses a persistent surface.
 * [actionLabel] enables the action; without it the bar shows the message only. The action keeps the
 * density minimum target and draws the shared focus outline.
 */
@Composable
fun HbSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier
            .clip(HbShapes.Medium)
            .background(colors.action.primaryBackground)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        BasicText(
            text = message,
            style = HbTypography.Label,
            color = { colors.action.primaryForeground },
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            SnackbarAction(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun SnackbarAction(
    label: String,
    onClick: () -> Unit,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .clip(HbShapes.Small)
            .heightIn(min = HbSizes.minimumTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HbSpacing.Xs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { colors.action.primaryForeground },
        )
    }
}
