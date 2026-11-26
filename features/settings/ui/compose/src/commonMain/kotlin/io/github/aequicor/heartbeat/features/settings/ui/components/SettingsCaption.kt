package io.github.aequicor.heartbeat.features.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbBrandLogo
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption

@Composable
internal fun SettingsCaption(modifier: Modifier = Modifier) {
    val caption = HbWindowCaption.current
    if (caption.height <= Dp.Hairline) return
    Box(modifier.fillMaxWidth().height(caption.height).background(HbTheme.colors.surface.raised)) {
        val position = if (caption.leftInset > Dp.Hairline) Alignment.Center else Alignment.CenterStart
        HbBrandLogo(Modifier.align(position).padding(horizontal = HbSpacing.M))
        HbDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}
