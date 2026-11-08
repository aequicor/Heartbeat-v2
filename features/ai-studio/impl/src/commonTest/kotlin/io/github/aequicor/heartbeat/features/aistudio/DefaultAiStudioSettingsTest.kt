package io.github.aequicor.heartbeat.features.aistudio

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.features.aistudio.di.DefaultAiStudioSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAiStudioSettingsTest {

    @Test
    fun startsFromTheStoredValueAndPersistsChanges() = runTest {
        val store = FakePreferencesStore()
        store.putBoolean("avatar_visible", false)
        val factory = object : PreferencesStoreFactory {
            override fun forNamespace(namespace: String): PreferencesStore {
                assertEquals("ai-studio", namespace)
                return store
            }
        }

        val settings = DefaultAiStudioSettings(factory, AppCoroutineScope(backgroundScope))

        assertEquals(false, settings.avatarVisible.first { !it })

        settings.setAvatarVisible(true)

        assertEquals(true, store.observeBoolean("avatar_visible").first())
        assertEquals(true, settings.avatarVisible.first { it })
    }
}

private class FakePreferencesStore : PreferencesStore {

    private val values = MutableStateFlow<Map<String, Any>>(mapOf())

    override fun observeString(key: String, default: String): Flow<String> =
        values.map { it[key] as? String ?: default }

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        values.map { it[key] as? Boolean ?: default }

    override fun observeLong(key: String, default: Long): Flow<Long> =
        values.map { it[key] as? Long ?: default }

    override suspend fun putString(key: String, value: String) {
        values.update { it + (key to value) }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        values.update { it + (key to value) }
    }

    override suspend fun putLong(key: String, value: Long) {
        values.update { it + (key to value) }
    }

    override suspend fun remove(key: String) {
        values.update { it - key }
    }
}
