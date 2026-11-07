package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

/**
 * Compact contextual task on a floating surface (HB-DS-026).
 *
 * The sheet owns its title, its close action and its content; the owner places it and decides
 * whether it is modal. The shape is the floating-surface radius, because a sheet overlaps the
 * content below it instead of becoming part of the layout.
 */
@Composable
fun HbSheet(
    title: String,
    onClose: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .clip(HbShapes.Large)
            .background(colors.surface.raised)
            .border(SHEET_BORDER, colors.border.subtle, HbShapes.Large)
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

private val SHEET_BORDER = 1.dp
