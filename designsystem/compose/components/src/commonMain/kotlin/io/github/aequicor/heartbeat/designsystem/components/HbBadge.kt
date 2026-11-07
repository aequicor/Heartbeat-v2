package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Compact status or count badge (HB-DS-006). The tone is the meaning and the label is always
 * present, so the badge is not read as color alone. A badge is display-only: it is not focusable
 * and never becomes the only place an action exists.
 */
@Composable
fun HbBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: HbStatusTone = HbStatusTone.Neutral,
) {
    val colors = HbTheme.colors

    Box(
        modifier = modifier
            .clip(HbShapes.Small)
            .background(tone.container(colors))
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xxs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = HbTypography.Metadata,
            color = { tone.foreground(colors) },
        )
    }
}
