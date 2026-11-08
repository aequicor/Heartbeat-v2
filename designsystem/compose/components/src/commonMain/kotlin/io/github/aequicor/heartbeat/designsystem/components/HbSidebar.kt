package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Sidebar of the workspace (HB-DS-032): a bounded secondary column with a header and its own
 * scrolling content.
 *
 * The width stays inside the documented sidebar range, so a wide window does not turn the sidebar
 * into a second reading column; the owner still decides the actual [Modifier.width] and the fit
 * policy decides whether the sidebar is visible at all. The header has a stable height, the content
 * scrolls inside the column, and the surface uses the subtle role instead of the canvas.
 */
@Composable
fun HbSidebar(
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = HbPaneGeometry.SidebarMin.dp, max = HbPaneGeometry.SidebarMax.dp)
            .background(colors.surface.subtle),
    ) {
        if (title != null || actions != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HbSizes.paneHeaderHeight)
                    .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
            ) {
                if (title != null) {
                    BasicText(
                        text = title,
                        style = HbTypography.PaneTitle,
                        color = { colors.text.primary },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Row(modifier = Modifier.weight(1f)) {}
                }
                actions?.invoke(this)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(HbSpacing.S),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
            content = content,
        )
    }
}
