package io.github.aequicor.heartbeat.features.settings.api

/**
 * Metadata of one settings page. It describes how the page appears in the flat sidebar list and
 * carries no UI: the page content is provided separately by the owning feature.
 *
 * The [title] is the display copy the contributor owns; [keywords] widen the sidebar search beyond
 * the title; [order] defines the position, with the id as a stable tie-breaker.
 */
public data class SettingsPageDescriptor(
    public val id: SettingsPageId,
    public val title: String,
    public val keywords: Set<String> = setOf(),
    public val order: Int = 0,
)
