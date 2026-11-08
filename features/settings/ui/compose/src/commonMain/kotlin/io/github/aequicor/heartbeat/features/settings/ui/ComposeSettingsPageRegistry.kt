package io.github.aequicor.heartbeat.features.settings.ui

import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider

/**
 * Resolves the provider that renders a settings page. Providers register their page ids once; a
 * page id that appears in two providers is a configuration error, exactly like duplicate feature
 * renderers in the shell.
 */
internal class ComposeSettingsPageRegistry(
    providers: Set<ComposeSettingsPageProvider>,
) {

    private val byPageId: Map<String, ComposeSettingsPageProvider> =
        providers
            .flatMap { provider -> provider.pageIds.map { it to provider } }
            .toMap()
            .also { map ->
                val pageCount = providers.sumOf { it.pageIds.size }
                require(map.size == pageCount) {
                    "Duplicate settings page providers"
                }
            }

    fun find(pageId: String): ComposeSettingsPageProvider? = byPageId[pageId]
}
