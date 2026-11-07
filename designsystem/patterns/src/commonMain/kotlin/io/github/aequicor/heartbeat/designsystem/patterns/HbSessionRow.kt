package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.aequicor.heartbeat.designsystem.components.HbListItem
import io.github.aequicor.heartbeat.designsystem.components.HbStatusDot
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Session row of the workspace list (HB-DS-019).
 *
 * The row shows the title, the optional project and the visible state: running, unread or waiting
 * for approval. The state is marked by a tone dot and stays a subordinate signal; session actions
 * arrive through [actions] and never trigger the primary open action.
 */
@Composable
fun HbSessionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    project: String? = null,
    state: HbSessionState = HbSessionState.Idle,
    selected: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = HbThemeDefaults.LocalColors.current

    HbListItem(
        onClick = onClick,
        modifier = modifier
            .clip(HbShapes.Small)
            .background(if (selected) colors.selection.background else Color.Transparent),
        trailing = actions,
    ) {
        if (state != HbSessionState.Idle) {
            HbStatusDot(tone = state.tone())
        }
        Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs)) {
            BasicText(
                text = title,
                style = HbTypography.Label,
                color = { if (selected) colors.selection.foreground else colors.text.primary },
            )
            if (project != null) {
                BasicText(
                    text = project,
                    style = HbTypography.Caption,
                    color = { colors.text.secondary },
                )
            }
        }
    }
}

private fun HbSessionState.tone(): HbStatusTone = when (this) {
    HbSessionState.Idle -> HbStatusTone.Neutral
    HbSessionState.Running -> HbStatusTone.Info
    HbSessionState.Unread -> HbStatusTone.Neutral
    HbSessionState.Approval -> HbStatusTone.Warning
}
