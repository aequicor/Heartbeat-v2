package io.github.aequicor.heartbeat.features.settings.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.features.settings.component.SettingsComponent
import io.github.aequicor.heartbeat.features.settings.ui.components.SettingsContentHost
import io.github.aequicor.heartbeat.features.settings.ui.components.SettingsSidebar
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider

/**
 * Settings shell: a flat, searchable page list on the left and the selected page content on the
 * right. The shell owns only search, selection and the split layout; every page renders its own
 * content through the [ComposeSettingsPageProvider] that contributed it.
 */
@Composable
fun SettingsScreen(
    component: SettingsComponent,
    providers: Set<ComposeSettingsPageProvider>,
    modifier: Modifier = Modifier,
) {
    val registry = remember(providers) { ComposeSettingsPageRegistry(providers) }
    val selectedPageId by component.selectedPageId.collectAsState()
    val query by component.query.collectAsState()
    val selectedDescriptor = component.pages.firstOrNull { it.id == selectedPageId }

    Row(modifier = modifier.fillMaxSize()) {
        SettingsSidebar(
            pages = component.pages,
            selectedPageId = selectedPageId,
            query = query,
            onQueryChange = component::search,
            onSelect = component::select,
            onBack = component::back,
            modifier = Modifier.width(HbPaneGeometry.SidebarPreferred.dp),
        )
        HbDivider(vertical = true)
        SettingsContentHost(
            title = selectedDescriptor?.title,
            pageId = selectedPageId,
            registry = registry,
            modifier = Modifier.weight(1f),
        )
    }
}
