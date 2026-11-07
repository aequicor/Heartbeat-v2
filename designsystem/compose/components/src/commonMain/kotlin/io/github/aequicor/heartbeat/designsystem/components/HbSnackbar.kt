package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Brief confirmation or reversible action (HB-DS-026).
 *
 * The bar carries at most one action and announces itself politely, so the message reaches
 * assistive services without moving the focus. Its lifetime belongs to the owner: a snackbar is
 * never the only record of a consequential failure, and a decision uses a persistent surface.
 * [actionLabel] enables the action; without it the bar shows the message only.
 */
@Composable
fun HbSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = HbThemeDefaults.LocalColors.current

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
            BasicText(
                text = actionLabel,
                style = HbTypography.Label,
                color = { colors.action.primaryForeground },
                modifier = Modifier
                    .clip(HbShapes.Small)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = HbSpacing.Xs, vertical = HbSpacing.Xxs),
            )
        }
    }
}
