package io.github.aequicor.heartbeat.designsystem.patterns

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
import io.github.aequicor.heartbeat.designsystem.components.HbBadge
import io.github.aequicor.heartbeat.designsystem.components.HbStatusDot
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Row of a configured connection (HB-DS-019).
 *
 * The row names the connection and its provider, marks the availability with [statusLabel] and
 * a tone, and keeps the validation
 * result visible in [detail]; [actions] carries the recovery or edit action, because only the`r`n * owner
 * knows which recovery is possible. The availability is a tone plus a label, never a color alone.
 */
@Composable
fun HbConnectionRow(
    name: String,
    provider: String,
    status: HbConnectionStatus,
    statusLabel: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = HbThemeDefaults.LocalColors.current
    val tone = status.tone()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        HbStatusDot(tone = tone)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            BasicText(
                text = name,
                style = HbTypography.Label,
                color = { colors.text.primary },
            )
            BasicText(
                text = provider,
                style = HbTypography.Caption,
                color = { colors.text.secondary },
            )
            if (detail != null) {
                BasicText(
                    text = detail,
                    style = HbTypography.Caption,
                    color = { tone.foreground(colors) },
                )
            }
        }
        HbBadge(label = statusLabel, tone = tone)
        actions()
    }
}

private fun HbConnectionStatus.tone(): HbStatusTone = when (this) {
    HbConnectionStatus.Ready -> HbStatusTone.Success
    HbConnectionStatus.Checking -> HbStatusTone.Info
    HbConnectionStatus.Unavailable -> HbStatusTone.Neutral
    HbConnectionStatus.Failed -> HbStatusTone.Error
}

