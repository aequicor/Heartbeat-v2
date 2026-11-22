package io.github.aequicor.heartbeat.storage.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.storage.db.HeartbeatDatabase
import java.nio.file.Files
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

        assertEquals(8L, HeartbeatDatabase.Schema.version)
        assertEquals("Before restart", database.noteQueries.selectById("n1").executeAsOneOrNull()?.title)
        assertNull(database.noteQueries.selectById("missing").executeAsOneOrNull())
        second.close()
    }

    @Test
    fun versionTwoTreeStartsExpandedAndKeepsChangesAfterReopen() {
        val file = Files.createTempFile("heartbeat-expansion", ".db")
        val url = "jdbc:sqlite:$file"
        try {
            JdbcSqliteDriver(url).use { driver ->
                HeartbeatDatabase.Schema.migrate(driver, 1, 2)
                val database = HeartbeatDatabase(driver)
                database.studioProjectQueries.insertProject("p", "Project", "/project", 1, 0)
                database.studioSessionQueries.insertSession("root", "p", "Root", 1, 0, null)
                database.studioSessionQueries.insertSession("child", "p", "Child", 1, 0, "root")
                driver.execute(null, "PRAGMA user_version = 2", 0)
            }
            JdbcSqliteDriver(url, Properties(), HeartbeatDatabase.Schema).use { driver ->
                val database = HeartbeatDatabase(driver)
                assertEquals(1L, database.studioProjectQueries.selectProjectById("p").executeAsOne().is_expanded)
                val root = database.studioSessionQueries.selectSessionById("root").executeAsOne()
                assertEquals(1L, root.is_expanded)
                assertEquals("Idle", root.work_state)
                assertNull(root.workspace_payload)
                assertNull(root.workspace_summary)
                database.studioProjectQueries.updateProjectExpanded(0L, "p")
                database.studioSessionQueries.updateSessionExpanded(0L, "child")
            }
            JdbcSqliteDriver(url, Properties(), HeartbeatDatabase.Schema).use { driver ->
                val database = HeartbeatDatabase(driver)
                assertEquals(0L, database.studioProjectQueries.selectProjectById("p").executeAsOne().is_expanded)
                assertEquals(1L, database.studioSessionQueries.selectSessionById("root").executeAsOne().is_expanded)
                assertEquals(0L, database.studioSessionQueries.selectSessionById("child").executeAsOne().is_expanded)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun migrationGroupsExistingSessionsInTheSystemProjectAndKeepsNamedProjects() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            HeartbeatDatabase.Schema.migrate(driver, 1, 4)
            val database = HeartbeatDatabase(driver)
            val projects = database.studioProjectQueries
            val sessions = database.studioSessionQueries
            projects.insertProject("old", ":ungrouped:", "", 0, 0)
            projects.updateProjectExpanded(0, "old")
            projects.insertProject("demo", "imported-demo", "", 0, 0)
            projects.updateProjectTitle("Без проекта", 0, 0, "demo")
            projects.insertProject("user", "Без проекта", "/work/user", 0, 0)
            sessions.insertSession("first", "old", "First", 0, 0, null)
            sessions.insertSession("child", "old", "Reply", 1, 0, "first")
            sessions.insertSession("imported", "demo", "Imported", 0, 0, null)
            sessions.insertSession("named", "user", "Named", 0, 0, null)

            HeartbeatDatabase.Schema.migrate(driver, 4, 5)

            val systemId = "00000000-0000-0000-0000-000000000000"
            val system = projects.selectProjectById(systemId).executeAsOne()
            assertEquals("Без проекта", system.alias)
            assertEquals(0L, system.is_expanded)
            for (id in listOf("first", "child", "imported")) {
                assertEquals(systemId, driver.legacySessionLocation(id).projectId)
            }
            assertEquals("first", driver.legacySessionLocation("child").parentSessionId)
            assertEquals("user", driver.legacySessionLocation("named").projectId)
            assertNull(projects.selectProjectById("old").executeAsOneOrNull())
            assertNull(projects.selectProjectById("demo").executeAsOneOrNull())

            projects.insertProject("imported-project", "Imported project", "", 0, 0)
            sessions.insertSession("imported-root", "imported-project", "Imported root", 0, 0, null)
            sessions.insertSession("imported-child", "imported-project", "Reply", 1, 0, "imported-root")
            database.studioWorkspaceQueries.updateWorkspaceSession("payload", "summary", "imported-child")

            HeartbeatDatabase.Schema.migrate(driver, 5, 7)

            assertNull(projects.selectProjectById("imported-project").executeAsOneOrNull())
            assertEquals(systemId, sessions.selectSessionById("imported-root").executeAsOne().project_id)
            val importedChild = sessions.selectSessionById("imported-child").executeAsOne()
            assertEquals(systemId, importedChild.project_id)
            assertEquals("imported-root", importedChild.parent_session_id)
            assertEquals("payload", importedChild.workspace_payload)
            assertEquals("ACTIVE", importedChild.status)
            assertEquals("Idle", importedChild.work_state)
            assertEquals("user", sessions.selectSessionById("named").executeAsOne().project_id)
            assertEquals(0L, projects.selectProjectById(systemId).executeAsOne().is_expanded)
        }
    }

    @Test
    fun aggregateSchemaListsEveryComposedTable() {
        val tables = HeartbeatDatabase.allTableNames()

        assertTrue("note" in tables)
        assertTrue("chat" in tables)
        assertTrue("studio_project" in tables)
        assertTrue("studio_session" in tables)
        assertTrue("studio_workspace" in tables)
        assertTrue("preference" in tables)
    }
}

private data class LegacySessionLocation(val projectId: String?, val parentSessionId: String?)

private fun SqlDriver.legacySessionLocation(id: String): LegacySessionLocation = executeQuery(
    identifier = null,
    sql = "SELECT project_id, parent_session_id FROM studio_session WHERE id = ?",
    mapper = { cursor ->
        check(cursor.next().value)
        QueryResult.Value(LegacySessionLocation(cursor.getString(0), cursor.getString(1)))
    },
    parameters = 1,
    binders = { bindString(0, id) },
).value
