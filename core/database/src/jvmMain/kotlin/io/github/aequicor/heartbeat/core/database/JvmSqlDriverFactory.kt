package io.github.aequicor.heartbeat.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/** JVM driver for the desktop application; the directory is supplied by the entry point. */
public class JvmSqlDriverFactory(
    private val directory: String,
) : SqlDriverFactory {

    override fun create(
        databaseName: String,
        schema: SqlSchema<QueryResult.Value<Unit>>,
    ): SqlDriver {
        val databaseFile = File(directory, databaseName)
        databaseFile.parentFile?.mkdirs()
        return JdbcSqliteDriver(
            "jdbc:sqlite:${databaseFile.absolutePath}",
            Properties(),
            schema,
        )
    }
}
