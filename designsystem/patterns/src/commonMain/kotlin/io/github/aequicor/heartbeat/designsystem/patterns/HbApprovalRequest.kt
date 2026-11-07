package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbStatusDot
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Request for an explicit decision (HB-DS-030).
 *
 * The request names the action, the target, the context and the scope of the permission, so a
 * decision never relies on a vague default. [decisions] renders the active controls and is hidden
 * once the request is resolved: a resolved, expired or superseded request keeps its history but
 * loses its buttons. The controls come from trusted application data, never from generated prose.
 */
@Composable
fun HbApprovalRequest(
    action: String,
    target: String,
    modifier: Modifier = Modifier,
    state: HbApprovalState = HbApprovalState.Pending,
    context: String? = null,
    scope: String? = null,
    decisions: @Composable RowScope.() -> Unit = {},
) {
    val colors = HbThemeDefaults.LocalColors.current
    val tone = state.tone()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HbShapes.Medium)
            .background(tone.container(colors))
            .border(APPROVAL_BORDER, tone.foreground(colors), HbShapes.Medium)
            .padding(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            HbStatusDot(tone = tone)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
            ) {
                BasicText(
                    text = action,
                    style = HbTypography.Label,
                    color = { tone.foreground(colors) },
                )
                BasicText(
                    text = target,
                    style = HbTypography.Code,
                    color = { tone.foreground(colors) },
                )
                if (context != null) {
                    BasicText(
                        text = context,
                        style = HbTypography.Caption,
                        color = { tone.foreground(colors) },
                    )
                }
                if (scope != null) {
                    BasicText(
                        text = scope,
                        style = HbTypography.Caption,
                        color = { tone.foreground(colors) },
                    )
                }
            }
        }
        if (state == HbApprovalState.Pending) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                content = decisions,
            )
        }
    }
}

private fun HbApprovalState.tone(): HbStatusTone = when (this) {
    HbApprovalState.Pending -> HbStatusTone.Warning
    HbApprovalState.Allowed -> HbStatusTone.Success
    HbApprovalState.Rejected -> HbStatusTone.Error
    HbApprovalState.Expired -> HbStatusTone.Neutral
}

private val APPROVAL_BORDER = 1.dp
