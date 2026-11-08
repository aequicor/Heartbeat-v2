package io.github.aequicor.heartbeat.core.preferences.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/**
 * [PreferencesStoreFactory] backed by one Preferences DataStore file.
 *
 * The factory creates the single DataStore instance for [directory] and hands out namespaced
 * views of it, so two features never share a physical key. DataStore is created once per process
 * on purpose: multiple instances for the same file break its consistency guarantees.
 *
 * Read and write errors are not caught: a corrupted file or a failed write reaches the caller
 * instead of silently falling back to defaults.
 *
 * @param directory existing or creatable directory that holds the preferences file.
 * @param scope scope that owns DataStore IO; cancel it when the application shuts down.
 */
public class DataStorePreferencesStoreFactory(
    directory: String,
    scope: CoroutineScope,
) : PreferencesStoreFactory {

    private val dataStore: DataStore<Preferences>

    init {
        require(directory.isNotBlank()) { "The preferences directory must not be blank" }
        dataStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
            "$directory/$PREFERENCES_FILE".toPath()
        }
    }

    override fun forNamespace(namespace: String): PreferencesStore {
        require(namespace.isNotBlank()) { "The preferences namespace must not be blank" }
        require('.' !in namespace) {
            "The preferences namespace must be a single stable segment: $namespace"
        }
        return DataStorePreferencesStore(dataStore, namespace)
    }

    private companion object {
        private const val PREFERENCES_FILE = "heartbeat.preferences_pb"
    }
}

private class DataStorePreferencesStore(
    private val dataStore: DataStore<Preferences>,
    private val namespace: String,
) : PreferencesStore {

    override fun observeString(key: String, default: String): Flow<String> =
        dataStore.data.map { preferences -> preferences[stringKey(key)] ?: default }

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[booleanKey(key)] ?: default }

    override fun observeLong(key: String, default: Long): Flow<Long> =
        dataStore.data.map { preferences -> preferences[longKey(key)] ?: default }

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { preferences -> preferences[stringKey(key)] = value }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { preferences -> preferences[booleanKey(key)] = value }
    }

    override suspend fun putLong(key: String, value: Long) {
        dataStore.edit { preferences -> preferences[longKey(key)] = value }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { preferences -> preferences.remove(stringKey(key)) }
    }

    private fun physicalKey(key: String): String {
        require(key.isNotBlank()) { "The preferences key must not be blank" }
        return "$namespace.$key"
    }

    private fun stringKey(key: String) = stringPreferencesKey(physicalKey(key))

    private fun booleanKey(key: String) = booleanPreferencesKey(physicalKey(key))

    private fun longKey(key: String) = longPreferencesKey(physicalKey(key))
}
