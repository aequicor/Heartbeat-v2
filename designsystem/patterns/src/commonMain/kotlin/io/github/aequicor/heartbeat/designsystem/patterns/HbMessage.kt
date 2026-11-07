package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * One conversation message (HB-DS-023).
 *
 * The message owns its role and content structure, not its identity: the transcript keeps the
 * stable identifier and the expanded state. A user message uses a quiet tinted surface, assistant
 * prose stays on the reading surface, and the content is composed by the caller, so a paragraph is
 * not wrapped in a bubble. [roleLabel] names the author for assistive services without merging the
 * message actions into the same node.
 */
@Composable
fun HbMessage(
    role: HbMessageRole,
    roleLabel: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HbShapes.Medium)
            .background(
                if (role == HbMessageRole.User) {
                    colors.surface.subtle
                } else {
                    Color.Transparent
                },
            )
            .semantics { contentDescription = roleLabel }
            .padding(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        content()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (meta != null) {
                    BasicText(
                        text = meta,
                        style = HbTypography.Metadata,
                        color = { colors.text.secondary },
                    )
                }
            }
            actions()
        }
    }
}
