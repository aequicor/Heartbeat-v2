package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbBadge
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Ordered queue of accepted messages (HB-DS-020).
 *
 * The queue keeps the order it receives: moving a pane or changing the layout must not reorder it.
 * Each row shows its position, the message and the localized delivery state; the owner supplies
 * the state label and renders the supported edit, remove and reorder actions through [actions].
 */
@Composable
fun HbMessageQueue(
    items: List<HbQueueItem>,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(HbQueueItem) -> Unit = {},
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HbShapes.Medium)
            .background(colors.surface.subtle)
            .padding(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        items.forEachIndexed { index, item ->
            key(item.id) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                ) {
                    BasicText(
                        text = "${index + 1}",
                        style = HbTypography.Metadata,
                        color = { colors.text.secondary },
                        modifier = Modifier.width(INDEX_WIDTH),
                    )
                    BasicText(
                        text = item.label,
                        style = HbTypography.Label,
                        color = { colors.text.primary },
                        modifier = Modifier.weight(1f),
                    )
                    HbBadge(
                        label = item.stateLabel,
                        tone = item.state.tone(),
                    )
                    actions(item)
                }
            }
        }
    }
}

private fun HbQueueItemState.tone(): HbStatusTone = when (this) {
    HbQueueItemState.Accepted -> HbStatusTone.Info
    HbQueueItemState.Queued -> HbStatusTone.Neutral
    HbQueueItemState.Sending -> HbStatusTone.Info
    HbQueueItemState.Failed -> HbStatusTone.Error
}

private val INDEX_WIDTH = 16.dp
