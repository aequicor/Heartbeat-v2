package io.github.aequicor.heartbeat.core.preferences.api

/**
 * Creates a [PreferencesStore] for one feature namespace.
 *
 * The namespace is a stable part of the physical key (`ai-studio.avatar_visible`), so it must not
 * change between application versions: a rename would orphan the stored values. The factory is
 * application-scoped and owns the single physical preferences instance of the process.
 */
public interface PreferencesStoreFactory {

    public fun forNamespace(namespace: String): PreferencesStore
}
