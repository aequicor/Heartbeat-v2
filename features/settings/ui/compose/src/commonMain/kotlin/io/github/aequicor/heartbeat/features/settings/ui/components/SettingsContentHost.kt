package io.github.aequicor.heartbeat.features.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId
import io.github.aequicor.heartbeat.features.settings.component.SettingsComponent
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider

/** Hosts the selected feature within the right pane of the settings screen. */
@Composable
internal fun SettingsContentHost(
    pageId: SettingsPageId?,
    provider: ComposeSettingsPageProvider?,
    component: SettingsComponent,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(HbTheme.colors.surface.canvas), contentAlignment = Alignment.Center) {
        when {
            pageId == null -> HbText(
                text = "Раздел не выбран", style = HbTypography.Detail, modifier = Modifier.padding(HbSpacing.L),
            )
            provider == null -> HbText(
                text = "Раздел недоступен: ${pageId.value}", style = HbTypography.Detail,
                modifier = Modifier.padding(HbSpacing.L),
            )
            else -> key(pageId) { provider.Content(pageId.value, component) }
        }
    }
}
