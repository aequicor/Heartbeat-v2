package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/**
 * Non-modal side panel for sustained secondary work (HB-DS-026).
 *
 * The inspector has its own heading, its own close action and a bounded content area; it does not
 * block the content beside it. On a compact layout the owner replaces it with a destination or a
 * sheet, so the inspector never becomes a modal surface by accident.
 */
@Composable
fun HbInspector(
    title: String,
    onClose: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.surface.base),
    ) {
        HbDivider(vertical = true)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(HbSpacing.S),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        ) {
            HbPanelHeader(
                title = title,
                onClose = onClose,
                closeLabel = closeLabel,
                actions = actions,
            )
            content()
        }
    }
}
