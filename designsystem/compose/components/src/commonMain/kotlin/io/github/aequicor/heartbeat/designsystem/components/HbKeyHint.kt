package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Shortcut hint for supplementary information (HB-DS-001). The hint renders the key names it is
 * given; the platform mapping and the shortcut itself belong to the host, so a hint never becomes
 * the only place an action is documented.
 */
@Composable
fun HbKeyHint(
    keys: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            Box(
                modifier = Modifier
                    .clip(HbShapes.Small)
                    .background(colors.surface.subtle)
                    .border(KEY_BORDER, colors.border.control, HbShapes.Small)
                    .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xxs),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = key,
                    style = HbTypography.Metadata,
                    color = { colors.text.secondary },
                )
            }
        }
    }
}

private val KEY_BORDER = 1.dp
