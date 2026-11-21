package io.github.aequicor.heartbeat.storage.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.storage.db.HeartbeatDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the aggregate schema of two independent feature modules plus the preferences
 * fallback: the generated database creates every table on a new database, feature query APIs run
 * on the shared driver, and reopening the file keeps existing rows.
 */
class HeartbeatDatabaseTest {

    @Test
    fun aggregateSchemaCreatesEveryFeatureTable() {
        val driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties(),
            HeartbeatDatabase.Schema,
        )
        val database = HeartbeatDatabase(driver)

        database.noteQueries.upsert("n1", "Заголовок", "Содержимое")
        database.chatQueries.insert("c1", "Чат")
        database.preferencesQueries.upsert("ai-studio", "avatar_visible", "boolean", "false")

        assertEquals("Заголовок", database.noteQueries.selectById("n1").executeAsOneOrNull()?.title)
        assertEquals("Чат", database.chatQueries.selectById("c1").executeAsOneOrNull()?.title)
        assertEquals(
            "false",
            database.preferencesQueries.selectByKey("ai-studio", "avatar_visible")
                .executeAsOneOrNull()
                ?.value_,
        )
        driver.close()
    }

    @Test
    fun existingRowsSurviveReopenWithTheSameSchemaVersion() {
        val directory = Files.createTempDirectory("heartbeat-database")
        val file = directory.resolve("heartbeat.db")

        val first = JdbcSqliteDriver(
            "jdbc:sqlite:${file.toAbsolutePath()}",
            Properties(),
            HeartbeatDatabase.Schema,
        )
        HeartbeatDatabase(first).noteQueries.upsert("n1", "Before restart", "Body")
        first.close()

        val second = JdbcSqliteDriver(
            "jdbc:sqlite:${file.toAbsolutePath()}",
            Properties(),
            HeartbeatDatabase.Schema,
        )
        val database = HeartbeatDatabase(second)

        assertEquals(2L, HeartbeatDatabase.Schema.version)
        assertEquals("Before restart", database.noteQueries.selectById("n1").executeAsOneOrNull()?.title)
        assertNull(database.noteQueries.selectById("missing").executeAsOneOrNull())
        second.close()
    }

    @Test
    fun migrationFromVersionOnePreservesExistingRows() {
        val file = Files.createTempFile("heartbeat-database-v1", ".db")
        try {
            val baseline = Path.of("src", "commonMain", "sqldelight", "databases", "1.db")
            Files.copy(baseline, file, StandardCopyOption.REPLACE_EXISTING)
            JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").use { driver ->
                driver.execute(null, "INSERT INTO chat(id, title) VALUES ('old', 'Before migration')", 0)
                driver.execute(null, "INSERT INTO note(id, title, content) VALUES ('n1', 'Old note', 'Body')", 0)

                HeartbeatDatabase.Schema.migrate(driver, 1L, HeartbeatDatabase.Schema.version)

                val database = HeartbeatDatabase(driver)
                assertEquals("Before migration", database.chatQueries.selectById("old").executeAsOne().title)
                assertEquals("Body", database.noteQueries.selectById("n1").executeAsOne().content)
                assertTrue(database.chatQueries.selectSnapshots().executeAsList().isEmpty())
                assertNull(database.chatQueries.selectWorkspace().executeAsOneOrNull())
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun aggregateSchemaListsEveryComposedTable() {
        val tables = HeartbeatDatabase.allTableNames()

        assertTrue("note" in tables)
        assertTrue("chat" in tables)
        assertTrue("chat_workspace" in tables)
        assertTrue("preference" in tables)
    }
}
