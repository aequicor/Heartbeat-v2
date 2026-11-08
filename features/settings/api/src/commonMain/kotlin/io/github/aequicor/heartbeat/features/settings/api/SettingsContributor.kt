package io.github.aequicor.heartbeat.features.settings.api

/**
 * Declares the settings pages a feature owns. A feature contributes only metadata here; the page
 * content is registered through a renderer adapter such as `ComposeSettingsPageProvider`.
 */
public interface SettingsContributor {
    public val pages: List<SettingsPageDescriptor>
}
