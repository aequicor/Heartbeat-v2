package io.github.aequicor.heartbeat.storage.database

import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.core.database.SqlDriverFactory
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.storage.db.HeartbeatDatabase
import kotlin.concurrent.Volatile

/**
 * Opens and closes the single physical database of the application.
 *
 * The initializer owns the aggregate schema: it creates the driver through the platform
 * [SqlDriverFactory] and passes [HeartbeatDatabase.Schema], which composes every feature schema
 * and the global migration sequence. Features never create or migrate a schema themselves.
 *
 * [initialize] must run during application startup before the first repository is used. A failure
 * (for example a broken migration) propagates to the caller, which must report it and stop
 * dependent features; the initializer never deletes or recreates user data.
 */
@SingleIn(AppScope::class)
public class DatabaseInitializer @Inject constructor(
    private val driverFactory: SqlDriverFactory,
) {

    @Volatile
    private var driver: SqlDriver? = null

    /** Creates the driver and applies the aggregate schema; repeated calls are no-ops. */
    public fun initialize() {
        if (driver != null) return
        driver = driverFactory.create(DATABASE_NAME, HeartbeatDatabase.Schema)
    }

    /** Closes the connection and releases the file; later use requires a new [initialize]. */
    public fun close() {
        driver?.close()
        driver = null
    }

    internal fun requireDriver(): SqlDriver =
        driver ?: error("The Heartbeat database is not initialized; call DatabaseInitializer.initialize() first")

    private companion object {
        private const val DATABASE_NAME = "heartbeat.db"
    }
}
