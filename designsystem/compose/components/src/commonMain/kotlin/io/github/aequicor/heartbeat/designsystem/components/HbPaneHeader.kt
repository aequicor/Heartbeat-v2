package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Header of one workspace pane (HB-DS-032). The pane keeps its own title, so a narrow window still
 * shows which pane holds the focus; actions stay in the header and never hide the pane identity.
 */
@Composable
fun HbPaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = HbThemeDefaults.LocalColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.paneHeaderHeight)
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            BasicText(
                text = title,
                style = HbTypography.PaneTitle,
                color = { colors.text.primary },
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = HbTypography.Caption,
                    color = { colors.text.secondary },
                )
            }
        }
        actions()
    }
}

