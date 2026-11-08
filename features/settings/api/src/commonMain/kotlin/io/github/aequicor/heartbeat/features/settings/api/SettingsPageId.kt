package io.github.aequicor.heartbeat.features.settings.api

/**
 * Stable identifier of a settings page, owned by the feature that provides the page. The value is
 * used by the settings registry to deduplicate pages and by the shell to select a page.
 */
@JvmInline
public value class SettingsPageId(public val value: String)
