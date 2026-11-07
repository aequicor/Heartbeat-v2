package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbGeometries
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing

/**
 * Compact row of pane actions (HB-DS-032). Optional text labels are removed before an action moves
 * into the labeled overflow, and the only Stop or pending-approval action is never hidden to make
 * the row fit; the owner places the overflow control in [actions].
 */
@Composable
fun HbToolbar(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOOLBAR_MIN_HEIGHT)
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        content = actions,
    )
}

private val TOOLBAR_MIN_HEIGHT = HbGeometries.Dense.controlHeight.dp
