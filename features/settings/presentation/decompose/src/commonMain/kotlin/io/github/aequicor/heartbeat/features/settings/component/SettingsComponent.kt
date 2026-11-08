package io.github.aequicor.heartbeat.features.settings.component

import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot
import kotlinx.coroutines.flow.StateFlow

/**
 * Decompose adapter of the settings shell: the flat page list, the selected page and the search
 * query. Page selection is local navigation inside the settings feature; the shell layout and the
 * page content are owned by the Compose UI layer.
 */
interface SettingsComponent : FeatureRoot {

    val pages: List<SettingsPageDescriptor>

    /** Selected page, or `null` when the registry has no pages. */
    val selectedPageId: StateFlow<SettingsPageId?>

    val query: StateFlow<String>

    fun select(pageId: SettingsPageId)

    fun search(query: String)

    /** Leaves the settings shell and returns to the previous screen. */
    fun back()
}
