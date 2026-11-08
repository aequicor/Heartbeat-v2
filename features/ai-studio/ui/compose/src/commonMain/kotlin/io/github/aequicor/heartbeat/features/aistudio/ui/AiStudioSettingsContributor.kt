package io.github.aequicor.heartbeat.features.aistudio.ui

import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.settings.api.SettingsContributor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId

/** Declares the AI studio settings page in the settings registry. */
@ContributesIntoSet(AppScope::class)
class AiStudioSettingsContributor : SettingsContributor {

    override val pages: List<SettingsPageDescriptor> = listOf(
        SettingsPageDescriptor(
            id = SettingsPageId(PAGE_ID),
            title = "ИИ-студия",
            keywords = setOf("аватар", "профиль", "студия"),
            order = 100,
        ),
    )

    companion object {

        /** Page id of the AI studio settings page, shared with the page provider. */
        const val PAGE_ID: String = "ai-studio"
    }
}
