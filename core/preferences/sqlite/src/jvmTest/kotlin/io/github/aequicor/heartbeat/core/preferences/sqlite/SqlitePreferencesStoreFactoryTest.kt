package io.github.aequicor.heartbeat.core.preferences.sqlite

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.core.preferences.sqlite.db.HeartbeatDatabase
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * SQLite fallback checks for the platforms where DataStore has no artifact. The store shares the
 * aggregate database and must provide the same namespace isolation and persistence as the
 * DataStore implementation.
 */
class SqlitePreferencesStoreFactoryTest {

    @Test
    fun namespacesAreIsolatedAndPersistenceWorks() = runBlocking {
        val directory = Files.createTempDirectory("heartbeat-sqlite-preferences")
        val file = directory.resolve("heartbeat.db")

        val firstDriver = JdbcSqliteDriver(
            "jdbc:sqlite:${file.toAbsolutePath()}",
            Properties(),
            HeartbeatDatabase.Schema,
        )
        val first = SqlitePreferencesStoreFactory(firstDriver)
        val aiStudio = first.forNamespace("ai-studio")
        val editor = first.forNamespace("editor")

        aiStudio.putString("engine", "reasoning")
        aiStudio.putBoolean("avatar_visible", false)
        aiStudio.putLong("history_size", 20L)

        assertEquals("reasoning", aiStudio.observeString("engine").first())
        assertEquals(false, aiStudio.observeBoolean("avatar_visible", default = true).first())
        assertEquals(20L, aiStudio.observeLong("history_size").first())
        assertEquals("", editor.observeString("engine").first())
        firstDriver.close()

        val secondDriver = JdbcSqliteDriver(
            "jdbc:sqlite:${file.toAbsolutePath()}",
            Properties(),
            HeartbeatDatabase.Schema,
        )
        try {
            val reopened = SqlitePreferencesStoreFactory(secondDriver).forNamespace("ai-studio")
            assertEquals("reasoning", reopened.observeString("engine").first())
            assertEquals(false, reopened.observeBoolean("avatar_visible", default = true).first())
            assertEquals(20L, reopened.observeLong("history_size").first())
        } finally {
            secondDriver.close()
        }
    }

    @Test
    fun readingAKeyWithAnotherTypeFailsExplicitly() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties(),
            HeartbeatDatabase.Schema,
        )
        try {
            val store = SqlitePreferencesStoreFactory(driver).forNamespace("settings")
            store.putLong("theme", 1L)

            assertFailsWith<IllegalStateException> {
                store.observeString("theme").first()
            }
        } finally {
            driver.close()
        }
    }
}
