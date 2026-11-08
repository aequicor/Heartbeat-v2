package io.github.aequicor.heartbeat.features.editor.data.datasource

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.editor.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.editor.domain.model.Note
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** SQLDelight data source: the feature query API runs on the shared driver contract. */
class SqlDelightEditorDataSourceTest {

    @Test
    fun savesUpdatesAndLoadsNotes() = runTest {
        val driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties(),
            HeartbeatDatabase.Schema,
        )
        val dataSource = SqlDelightEditorDataSource(connection(driver))

        assertNull(dataSource.find("n1"))

        dataSource.store(Note(id = "n1", title = "First", content = "Body"))
        assertEquals(Note(id = "n1", title = "First", content = "Body"), dataSource.find("n1"))

        dataSource.store(Note(id = "n1", title = "Updated", content = "New body"))
        assertEquals(Note(id = "n1", title = "Updated", content = "New body"), dataSource.find("n1"))

        driver.close()
    }

    private fun connection(driver: SqlDriver): DatabaseConnection = object : DatabaseConnection {
        override val driver: SqlDriver = driver
    }
}
