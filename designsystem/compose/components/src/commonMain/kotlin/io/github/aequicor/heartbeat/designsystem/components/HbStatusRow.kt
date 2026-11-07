package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Compact status row for a connection or an operation (HB-DS-026). The tone is carried by the
 * indicator and the [label] always names the state, so the meaning is not encoded by color alone.
 * A row is display-only: details and recovery actions stay with the owner of the operation.
 */
@Composable
fun HbStatusRow(
    label: String,
    modifier: Modifier = Modifier,
    tone: HbStatusTone = HbStatusTone.Neutral,
    detail: String? = null,
) {
    val colors = HbThemeTokens.colors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Box(
            modifier = Modifier
                .size(STATUS_INDICATOR)
                .clip(CircleShape)
                .background(tone.foreground(colors)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs)) {
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
    }
}

private val STATUS_INDICATOR = 8.dp
