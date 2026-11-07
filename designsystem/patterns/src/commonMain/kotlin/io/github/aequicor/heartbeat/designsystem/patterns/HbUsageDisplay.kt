package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * One usage value, such as context consumption or provider quota (HB-DS-026).
 *
 * Context and quota stay in separate displays, and an unknown or stale value is marked as such
 * instead of being replaced by a plausible number. [value] is already formatted by the owner,
 * including the "unavailable" wording; [detail] carries the period or reset information.
 */
@Composable
fun HbUsageDisplay(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    state: HbUsageState = HbUsageState.Known,
    detail: String? = null,
) {
    val colors = HbThemeDefaults.LocalColors.current
    val valueColor = when (state) {
        HbUsageState.Known -> colors.text.primary
        HbUsageState.Unknown, HbUsageState.Stale -> colors.text.secondary
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            BasicText(
                text = label,
                style = HbTypography.Label,
                color = { colors.text.primary },
            )
            if (detail != null) {
                BasicText(
                    text = detail,
                    style = HbTypography.Caption,
                    color = { colors.text.secondary },
                )
            }
        }
        BasicText(
            text = value,
            style = HbTypography.Metadata,
            color = { valueColor },
        )
    }
}
