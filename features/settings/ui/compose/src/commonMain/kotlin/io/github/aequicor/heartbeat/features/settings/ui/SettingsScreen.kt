package io.github.aequicor.heartbeat.features.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraSettingsTheme
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId
import io.github.aequicor.heartbeat.features.settings.component.SettingsComponent
import io.github.aequicor.heartbeat.features.settings.ui.components.SettingsCaption
import io.github.aequicor.heartbeat.features.settings.ui.components.SettingsContentHost
import io.github.aequicor.heartbeat.features.settings.ui.components.SettingsSearch
import io.github.aequicor.heartbeat.features.settings.ui.components.SettingsSidebar
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider.SearchResult
import kotlinx.coroutines.launch

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
    val provider = selectedPageId?.let { registry.find(it.value) }
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var searchTarget by remember { mutableStateOf<Pair<SettingsPageId, SearchResult>?>(null) }

    LaunchedEffect(searchTarget) {
        val (page, field) = searchTarget ?: return@LaunchedEffect
        field.open(component)
        component.select(page)
        searchTarget = null
    }

    fun dismissSearch() {
        searching = false
        component.search("")
    }

    fun leavePage(navigate: () -> Unit) {
        val current = component.selectedPageId.value
        val currentProvider = current?.let { registry.find(it.value) }
        if (current == null || currentProvider == null) {
            navigate()
        } else {
            scope.launch {
                currentProvider.requestLeave(current.value, component) {
                    if (component.selectedPageId.value == current) navigate()
                }
            }
        }
    }

    fun select(pageId: SettingsPageId, field: SearchResult? = null) {
        val open = {
            if (field == null) component.select(pageId)
            searchTarget = field?.let { pageId to it }
        }
        if (pageId == component.selectedPageId.value) open() else leavePage(open)
    }

    HbAuroraSettingsTheme {
        Column(modifier.fillMaxSize()) {
            SettingsCaption()
            Row(Modifier.weight(1f)) {
                SettingsSidebar(
                    pages = component.pages,
                    selectedPageId = selectedPageId,
                    registry = registry,
                    onSearch = { searching = true },
                    onSelect = { select(it) },
                    onBack = { leavePage(component::back) },
                    modifier = Modifier.width(HbAuroraLayout.SettingsNavigationWidth),
                )
                HbDivider(vertical = true)
                SettingsContentHost(selectedPageId, provider, component, Modifier.weight(1f))
            }
        }
        if (searching) {
            SettingsSearch(component.pages, registry, query, component::search, onSelect = { id, field ->
                dismissSearch()
                select(id, field)
            }, onDismiss = ::dismissSearch)
        }
    }
}
