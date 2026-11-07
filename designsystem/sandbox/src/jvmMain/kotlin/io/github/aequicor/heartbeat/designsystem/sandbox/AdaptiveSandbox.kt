@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbAdaptive
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Rendered adaptive classes at the documented widths (HB-DS-031, HB-DS-032). */
@Composable
internal fun AdaptiveSandboxScreen(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .background(colors.surface.canvas)
            .padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AdaptiveSample(width = 320)
        AdaptiveSample(width = 800)
        AdaptiveSample(width = 1300)
        AdaptiveSample(width = 1650)
    }
}

@Composable
private fun AdaptiveSample(
    width: Int,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    HbAdaptive(modifier = modifier.width(width.dp)) { windowWidth, fit ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.subtle)
                .padding(HbSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            BasicText(
                text = "$width · ${windowWidth.name}",
                style = HbTypography.Label,
                color = { colors.text.primary },
            )
            BasicText(
                text = "навигация ${fit.navigation.name} · боковая ${fit.sidebar} · " +
                    "инспектор ${fit.inspector} · вторая панель ${fit.secondPane}",
                style = HbTypography.Caption,
                color = { colors.text.secondary },
            )
        }
    }
}
