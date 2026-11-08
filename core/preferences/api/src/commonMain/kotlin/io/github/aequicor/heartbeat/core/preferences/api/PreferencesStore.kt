package io.github.aequicor.heartbeat.core.preferences.api

import kotlinx.coroutines.flow.Flow

/**
 * Persistent key-value settings of one feature namespace.
 *
 * A feature receives the store from [PreferencesStoreFactory] and uses local keys only; the
 * factory adds the namespace prefix. Values survive application restarts and are observed as a
 * [Flow] that emits the current value and every later change.
 *
 * The contract intentionally stays small: no generic `get<T>`, no serialization and no
 * schema-like API. Add a typed accessor only when a feature needs it.
 */
public interface PreferencesStore {

    /** Emits the stored string or [default] when the key is absent. */
    public fun observeString(
        key: String,
        default: String = "",
    ): Flow<String>

    /** Emits the stored boolean or [default] when the key is absent. */
    public fun observeBoolean(
        key: String,
        default: Boolean = false,
    ): Flow<Boolean>

    /** Emits the stored long or [default] when the key is absent. */
    public fun observeLong(
        key: String,
        default: Long = 0L,
    ): Flow<Long>

    public suspend fun putString(
        key: String,
        value: String,
    )

    public suspend fun putBoolean(
        key: String,
        value: Boolean,
    )

    public suspend fun putLong(
        key: String,
        value: Long,
    )

    public suspend fun remove(key: String)
}
