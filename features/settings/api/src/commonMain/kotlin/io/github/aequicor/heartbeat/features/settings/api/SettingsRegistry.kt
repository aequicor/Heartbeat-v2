package io.github.aequicor.heartbeat.features.settings.api

/**
 * Read-only view over every settings page contributed by the features. The registry is assembled by
 * the composition root from all [SettingsContributor]s and guarantees unique page ids and a stable
 * order ([SettingsPageDescriptor.order], then id).
 */
public interface SettingsRegistry {
    public val pages: List<SettingsPageDescriptor>
}
