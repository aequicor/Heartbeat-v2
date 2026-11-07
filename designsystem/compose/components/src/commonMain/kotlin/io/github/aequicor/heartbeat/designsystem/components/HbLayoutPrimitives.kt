package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbSpace

/**
 * Horizontal layout with a spacing token (HB-DS-009). The gap comes from the spacing scale, so a
 * screen does not introduce an unrelated measurement; alignment defaults to centered content, which
 * is the common row of an icon and a label.
 */
@Composable
fun HbRow(
    modifier: Modifier = Modifier,
    spacing: Int = HbSpace.Eight,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}

/** Vertical layout with a spacing token (HB-DS-009). */
@Composable
fun HbColumn(
    modifier: Modifier = Modifier,
    spacing: Int = HbSpace.Eight,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}

/**
 * Wrapping layout for compact metadata and actions (HB-DS-009). Items flow onto the next line when
 * the local width is insufficient, which is how chips and secondary actions survive a narrow pane
 * instead of shrinking.
 */
@Composable
fun HbFlowRow(
    modifier: Modifier = Modifier,
    spacing: Int = HbSpace.Six,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        content()
    }
}
