package io.github.aequicor.heartbeat.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.arkivanov.decompose.ComponentContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

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

    val navigationIcon: (@Composable (String, Color) -> Unit)? get() = null

    /** Matching controls within a page. A result prepares its control before the shell displays the page. */
    fun search(pageId: String, query: String): List<SearchResult> = listOf()

    class SearchResult(val label: String, val group: String, val open: suspend (ComponentContext) -> Unit)

    /** Run [leave] when this page can be left, after resolving any unsaved edits. */
    @Throwable(CancellationException::class)
    suspend fun requestLeave(pageId: String, context: ComponentContext, leave: () -> Unit) {
        leave()
    }

    /** Renders the content of [pageId], which is always one of [pageIds]. */
    @Composable
    fun Content(pageId: String, context: ComponentContext)
}
