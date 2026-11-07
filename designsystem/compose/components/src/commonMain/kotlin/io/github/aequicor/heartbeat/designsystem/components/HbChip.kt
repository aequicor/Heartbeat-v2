package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Compact metadata chip with content-aware sizing (HB-DS-018). A chip is display-only: it is not
 * focusable, so a filter chip needs the selection component instead of a clickable label.
 */
@Composable
fun HbChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tone: HbStatusTone = HbStatusTone.Neutral,
) {
    val colors = HbTheme.colors
    val background = if (selected) colors.selection.background else tone.container(colors)
    val contentColor = if (selected) colors.selection.foreground else tone.foreground(colors)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = HbTypography.Caption,
            color = { contentColor },
        )
    }
}
