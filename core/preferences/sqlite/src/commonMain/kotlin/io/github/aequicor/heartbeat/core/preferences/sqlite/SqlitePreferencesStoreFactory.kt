package io.github.aequicor.heartbeat.core.preferences.sqlite

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.core.preferences.sqlite.db.PreferencesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLite fallback for platforms without a DataStore artifact (currently `mingwX64`).
 *
 * The store uses the shared application driver, so the `preference` table is part of the
 * aggregated schema in `storage/database`; there is no second database file. The table is keyed by
 * `(namespace, key)`, which keeps feature namespaces isolated exactly like the DataStore
 * implementation.
 *
 * The driver must already be initialized with the aggregated schema when this factory is created.
 */
public class SqlitePreferencesStoreFactory(
    driver: SqlDriver,
) : PreferencesStoreFactory {

    private val queries = PreferencesQueries(driver)

    override fun forNamespace(namespace: String): PreferencesStore {
        require(namespace.isNotBlank()) { "The preferences namespace must not be blank" }
        require('.' !in namespace) {
            "The preferences namespace must be a single stable segment: $namespace"
        }
        return SqlitePreferencesStore(queries, namespace)
    }
}

@Suppress("InjectDispatcher") // Query execution is moved off the caller thread by design.
private class SqlitePreferencesStore(
    private val queries: PreferencesQueries,
    private val namespace: String,
) : PreferencesStore {

    override fun observeString(key: String, default: String): Flow<String> =
        observe(key).map { stored -> stored?.valueOf(STRING, key) ?: default }

    override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        observe(key).map { stored ->
            stored?.valueOf(BOOLEAN, key)?.toBooleanStrictOrNull() ?: default
        }

    override fun observeLong(key: String, default: Long): Flow<Long> =
        observe(key).map { stored -> stored?.valueOf(LONG, key)?.toLongOrNull() ?: default }

    override suspend fun putString(key: String, value: String) {
        upsert(key, STRING, value)
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        upsert(key, BOOLEAN, value.toString())
    }

    override suspend fun putLong(key: String, value: Long) {
        upsert(key, LONG, value.toString())
    }

    override suspend fun remove(key: String) {
        queries.delete(namespace, physicalKey(key))
    }

    private fun observe(key: String): Flow<StoredPreference?> =
        queries.selectByKey(namespace, physicalKey(key)) { type, value ->
            StoredPreference(type, value)
        }
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)

    private fun upsert(key: String, type: String, value: String) {
        queries.upsert(namespace, physicalKey(key), type, value)
    }

    private fun physicalKey(key: String): String {
        require(key.isNotBlank()) { "The preferences key must not be blank" }
        return "$namespace.$key"
    }

    private fun StoredPreference.valueOf(expectedType: String, localKey: String): String {
        check(type == expectedType) {
            "Preference $namespace.$localKey is stored as $type, not $expectedType"
        }
        return value
    }

    private data class StoredPreference(
        val type: String,
        val value: String,
    )

    private companion object {
        private const val STRING = "string"
        private const val BOOLEAN = "boolean"
        private const val LONG = "long"
    }
}
