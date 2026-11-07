package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Persistent workspace or connection condition (HB-DS-026).
 *
 * A banner explains the scope of the condition and its recovery; it is not a transient
 * confirmation, so it stays until the condition is resolved or the owner removes it. The recovery
 * action arrives through [actions], because only the owner knows what can be done.
 */
@Composable
fun HbBanner(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    tone: HbStatusTone = HbStatusTone.Info,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HbShapes.Medium)
            .background(tone.container(colors))
            .border(BANNER_BORDER, tone.foreground(colors), HbShapes.Medium)
            .padding(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        BasicText(
            text = title,
            style = HbTypography.Label,
            color = { tone.foreground(colors) },
        )
        if (message != null) {
            BasicText(
                text = message,
                style = HbTypography.Caption,
                color = { tone.foreground(colors) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
            content = actions,
        )
    }
}

private val BANNER_BORDER = 1.dp
