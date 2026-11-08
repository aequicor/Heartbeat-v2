package io.github.aequicor.heartbeat.features.settings.component

import com.arkivanov.decompose.ComponentContext
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId
import io.github.aequicor.heartbeat.features.settings.api.SettingsRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default settings component: holds only the local shell state (selection and search query). The
 * page list comes from the [SettingsRegistry]; the page content is rendered by the UI layer.
 */
internal class DefaultSettingsComponent(
    componentContext: ComponentContext,
    requestedPageId: String?,
    registry: SettingsRegistry,
) : SettingsComponent, ComponentContext by componentContext {

    override val pages: List<SettingsPageDescriptor> = registry.pages

    private val selectedPage = MutableStateFlow(initialPageId(requestedPageId))
    override val selectedPageId: StateFlow<SettingsPageId?> = selectedPage.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    override val query: StateFlow<String> = searchQuery.asStateFlow()

    override fun select(pageId: SettingsPageId) {
        selectedPage.value = pageId
    }

    override fun search(query: String) {
        searchQuery.value = query
    }

    private fun initialPageId(requestedPageId: String?): SettingsPageId? =
        requestedPageId
            ?.let { requested -> pages.firstOrNull { it.id.value == requested }?.id }
            ?: pages.firstOrNull()?.id
}
