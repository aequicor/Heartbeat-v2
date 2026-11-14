package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioSettings
import io.github.aequicor.heartbeat.features.aistudio.ui.settings.AiStudioSettingsPage
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider

/** Renders the AI studio settings page; the settings shell resolves it by page id. */
@ContributesIntoSet(AppScope::class)
class AiStudioSettingsPageProvider(
    private val settings: AiStudioSettings,
    private val appScope: AppCoroutineScope,
) : ComposeSettingsPageProvider {

    override val pageIds: Set<String> = setOf(AiStudioSettingsContributor.PAGE_ID)

    @Composable
    override fun Content(pageId: String) {
        when (pageId) {
            AiStudioSettingsContributor.PAGE_ID -> AiStudioSettingsPage(settings, appScope)
            else -> error("Unknown settings page: $pageId")
        }
    }
}
