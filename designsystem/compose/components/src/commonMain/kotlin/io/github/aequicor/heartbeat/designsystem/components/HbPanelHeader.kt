package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Header of a dismissible surface: heading, owner actions and an explicit close action. The close
 * control is named by [closeLabel], so the glyph is not its accessible name.
 */
@Composable
internal fun HbPanelHeader(
    title: String,
    onClose: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        BasicText(
            text = title,
            style = HbTypography.Title,
            color = { colors.text.primary },
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
        HbIconButton(
            contentDescription = closeLabel,
            onClick = onClose,
        ) {
            HbCloseGlyph(color = colors.text.secondary)
        }
    }
}
