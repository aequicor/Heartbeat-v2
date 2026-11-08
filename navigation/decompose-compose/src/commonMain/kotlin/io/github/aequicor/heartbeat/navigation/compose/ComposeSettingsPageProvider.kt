package io.github.aequicor.heartbeat.navigation.compose

import androidx.compose.runtime.Composable

/**
 * Renders the settings pages a feature owns. It is the Compose adapter of the settings metadata a
 * `SettingsContributor` declares: the metadata lives in `features:settings:api`, while this
 * renderer SPI stays in the navigation Compose module so features can implement it without
 * depending on another feature's UI.
 *
 * Page ids are plain strings for the same reason [ComposeFeatureRenderer.featureId] is: the renderer
 * SPI is a cross-feature boundary, and the typed identifier belongs to the owning contract.
 */
interface ComposeSettingsPageProvider {

    /** Page ids this provider can render. */
    val pageIds: Set<String>

    /** Renders the content of [pageId], which is always one of [pageIds]. */
    @Composable
    fun Content(pageId: String)
}
