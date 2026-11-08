package io.github.aequicor.heartbeat.core.database

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android driver for the application database.
 *
 * `AndroidSqliteDriver` keeps the database in the standard application database directory and
 * runs schema creation and migration inside the `SQLiteOpenHelper` callbacks.
 */
public class AndroidSqlDriverFactory(
    private val context: Context,
) : SqlDriverFactory {

    override fun create(
        databaseName: String,
        schema: SqlSchema<QueryResult.Value<Unit>>,
    ): SqlDriver = AndroidSqliteDriver(schema, context, databaseName)
}
