package io.github.aequicor.heartbeat.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * Kotlin/Native driver for the application database.
 *
 * The directory is passed to SQLiter through `extendedConfig.basePath`; the driver creates the
 * database file there and applies the aggregated schema version on open.
 *
 * The final native binary links the system SQLite library (`-lsqlite3`). SQLDelight's `linkSqlite`
 * option, enabled in this module, supplies that option for its compilations; a native application
 * module that assembles the final executable must enable it as well. Linux builds require the
 * SQLite development package (`libsqlite3-dev`).
 */
public class NativeSqlDriverFactory(
    private val directory: String,
) : SqlDriverFactory {

    override fun create(
        databaseName: String,
        schema: SqlSchema<QueryResult.Value<Unit>>,
    ): SqlDriver = NativeSqliteDriver(
        schema = schema,
        name = databaseName,
        onConfiguration = { configuration ->
            configuration.copy(
                extendedConfig = configuration.extendedConfig.copy(basePath = directory),
            )
        },
    )
}
