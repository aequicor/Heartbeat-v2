package io.github.aequicor.heartbeat.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Access to the initialized application database.
 *
 * The owner of the connection (`storage/database`) fails fast until the schema is created or
 * migrated, so a repository that is constructed early reports a clear error instead of silently
 * working with an empty or outdated database.
 */
public interface DatabaseConnection {

    public val driver: SqlDriver
}
