package io.github.aequicor.heartbeat.features.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbPaneHeader
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId
import io.github.aequicor.heartbeat.features.settings.ui.ComposeSettingsPageRegistry

/**
 * Right pane of the settings shell. It provides the container and the page title; the selected page
 * owns its padding, scrolling and composition, so the shell never renders feature content itself.
 */
@Composable
internal fun SettingsContentHost(
    title: String?,
    pageId: SettingsPageId?,
    registry: ComposeSettingsPageRegistry,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.surface.canvas),
    ) {
        if (title != null) {
            HbPaneHeader(
                title = title, titleStyle = HbTypography.Title,
                modifier = Modifier.padding(horizontal = HbSpacing.M),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val provider = pageId?.let { registry.find(it.value) }
            when {
                pageId == null -> HbText(
                    text = "Раздел не выбран", style = HbTypography.Detail, modifier = Modifier.padding(HbSpacing.L),
                )
                provider == null -> HbText(
                    text = "Раздел недоступен: ${pageId.value}", style = HbTypography.Detail,
                    modifier = Modifier.padding(HbSpacing.L),
                )
                else -> provider.Content(pageId.value)
            }
        }
    }
}
