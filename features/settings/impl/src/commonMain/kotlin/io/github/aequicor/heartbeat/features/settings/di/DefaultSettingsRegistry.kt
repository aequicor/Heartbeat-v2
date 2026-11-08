package io.github.aequicor.heartbeat.features.settings.di

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.settings.api.SettingsContributor
import io.github.aequicor.heartbeat.features.settings.api.SettingsPageDescriptor
import io.github.aequicor.heartbeat.features.settings.api.SettingsRegistry

/**
 * Metro binding of the settings registry: collects pages from every [SettingsContributor], rejects
 * duplicate page ids and sorts by [SettingsPageDescriptor.order], then by id.
 */
@ContributesBinding(AppScope::class)
class DefaultSettingsRegistry(
    contributors: Set<SettingsContributor>,
) : SettingsRegistry {

    override val pages: List<SettingsPageDescriptor> = contributors
        .flatMap(SettingsContributor::pages)
        .sortedWith(compareBy<SettingsPageDescriptor> { it.order }.thenBy { it.id.value })
        .also { descriptors ->
            val duplicates = descriptors
                .groupBy { it.id }
                .filterValues { it.size > 1 }
                .keys
            require(duplicates.isEmpty()) {
                "Duplicate settings pages: $duplicates"
            }
        }
}
