package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.settings.api.SettingsContributor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageId

@ContributesIntoSet(AppScope::class)
public class EngineSettingsContributor : SettingsContributor {

    override val pages = listOf(SettingsPageDescriptor(
        id = SettingsPageId(EngineSettingsPageId), title = "Движки", keywords = setOf("ACP", "чат"), order = 200,
    ))
}

// Shared by the contributor and page provider.
@Suppress("NoNonPrivateGlobalVariables")
internal const val EngineSettingsPageId = "engines"
