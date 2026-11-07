package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Group of navigation rows under one label (HB-DS-019). The label is product copy and is not
 * transformed; the group owns only the spacing between the label and its rows.
 */
@Composable
fun HbNavigationGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        BasicText(
            text = title,
            style = HbTypography.Caption,
            color = { colors.text.secondary },
            modifier = Modifier.padding(start = HbSpacing.S, top = HbSpacing.Xs),
        )
        content()
    }
}
