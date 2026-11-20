package io.github.aequicor.heartbeat.features.aistudio.ui

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import kotlinx.coroutines.flow.flowOf

internal object AuroraTestPreferences : PreferencesStoreFactory, PreferencesStore {
    override fun forNamespace(namespace: String) = this
    override fun observeString(key: String, default: String) = flowOf(default)
    override fun observeBoolean(key: String, default: Boolean) = flowOf(default)
    override fun observeLong(key: String, default: Long) = flowOf(default)
    override suspend fun putString(key: String, value: String) = Unit
    override suspend fun putBoolean(key: String, value: Boolean) = Unit
    override suspend fun putLong(key: String, value: Long) = Unit
    override suspend fun remove(key: String) = Unit
}
