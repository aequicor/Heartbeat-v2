package io.github.aequicor.heartbeat.features.settings.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.settings.ui.resources.Res
import io.github.aequicor.heartbeat.features.settings.ui.resources.ic_settings_pulse
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SettingsCaption(modifier: Modifier = Modifier) {
    val caption = HbWindowCaption.current
    if (caption.height <= Dp.Hairline) return
    Box(modifier.fillMaxWidth().height(caption.height).background(HbTheme.colors.surface.raised)) {
        if (caption.leftInset > Dp.Hairline) {
            HbText("Heartbeat", modifier = Modifier.align(Alignment.Center), style = HbTypography.Label)
        } else {
            Row(
                Modifier.align(Alignment.CenterStart).padding(start = HbSpacing.M, end = caption.rightInset),
                horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(Res.drawable.ic_settings_pulse), contentDescription = null,
                    modifier = Modifier.size(HbAuroraLayout.RequestGlyph),
                    colorFilter = ColorFilter.tint(HbTheme.colors.accent.foreground),
                )
                HbText("Heartbeat", style = HbTypography.Label)
            }
        }
        HbDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}
