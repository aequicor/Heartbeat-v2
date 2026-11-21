package io.github.aequicor.heartbeat.features.aistudio.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.aistudio.di.DefaultAiStudioStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import java.util.Properties

internal fun auroraTestStore(scope: CoroutineScope) =
    TestDatabaseConnection().let { connection ->
        checkNotNull(scope.coroutineContext[Job]).invokeOnCompletion { connection.driver.close() }
        DefaultAiStudioStoreFactory(AuroraTestPreferences, connection).create(scope)
    }

private class TestDatabaseConnection : DatabaseConnection {
    override val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), HeartbeatDatabase.Schema)
}

private object AuroraTestPreferences : PreferencesStoreFactory, PreferencesStore {
    override fun forNamespace(namespace: String) = this
    override fun observeString(key: String, default: String) = flowOf(default)
    override fun observeBoolean(key: String, default: Boolean) = flowOf(default)
    override fun observeLong(key: String, default: Long) = flowOf(default)
    override suspend fun putString(key: String, value: String) = Unit
    override suspend fun putBoolean(key: String, value: Boolean) = Unit
    override suspend fun putLong(key: String, value: Long) = Unit
    override suspend fun remove(key: String) = Unit
}
