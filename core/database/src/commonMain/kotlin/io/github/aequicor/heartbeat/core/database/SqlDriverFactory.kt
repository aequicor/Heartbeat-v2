package io.github.aequicor.heartbeat.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Creates the physical SQLDelight driver of the application.
 *
 * The platform composition root supplies the implementation because only it knows the storage
 * directory and the Android `Context`. The schema always belongs to the aggregated database in
 * `storage/database`; a feature never calls this factory and never creates a schema of its own.
 *
 * A factory must return a driver that points at the single application database file and must not
 * delete or recreate existing user data when the schema version does not match.
 */
public interface SqlDriverFactory {

    public fun create(
        databaseName: String,
        schema: SqlSchema<QueryResult.Value<Unit>>,
    ): SqlDriver
}
