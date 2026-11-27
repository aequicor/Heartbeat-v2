package io.github.aequicor.heartbeat.storage.database

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.core.database.SqlDriverFactory
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.workspace.AiStudioAttachmentMigration
import io.github.aequicor.heartbeat.storage.db.HeartbeatDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val attachmentMigration: AiStudioAttachmentMigration,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) {

    private val lifecycle = Mutex()

    @Volatile
    private var driver: SqlDriver? = null

    private val schema = object : SqlSchema<QueryResult.Value<Unit>> by HeartbeatDatabase.Schema {

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> {
            val aggregate = HeartbeatDatabase.Schema
            if (oldVersion >= ATTACHMENT_SCHEMA || newVersion < ATTACHMENT_SCHEMA) {
                return aggregate.migrate(driver, oldVersion, newVersion, *callbacks)
            }
            if (oldVersion < ATTACHMENT_PREPARATION_SCHEMA) {
                aggregate.migrate(driver, oldVersion, ATTACHMENT_PREPARATION_SCHEMA, *callbacks)
            }
            // Stay on the same driver connection and thread for the entire upgrade transaction.
            attachmentMigration.prepareForCutover(driver)
            val partSequence = driver.longValue("SELECT seq FROM sqlite_sequence WHERE name = 'message_part'")
            aggregate.migrate(driver, ATTACHMENT_PREPARATION_SCHEMA, ATTACHMENT_SCHEMA, *callbacks)
            if (partSequence != null) driver.preserveMessagePartSequence(partSequence)
            attachmentMigration.migrateStoredWorkspaceHistory(driver)
            return aggregate.migrate(driver, ATTACHMENT_SCHEMA, newVersion, *callbacks)
        }
    }

    /** Creates the driver and applies the aggregate schema; repeated calls are no-ops. */
    public fun initialize(): Unit = runBlocking {
        lifecycle.withLock {
            if (driver != null) return@withLock
            driver = withContext(ioDispatcher) {
                val opened = driverFactory.create(DATABASE_NAME, schema)
                try {
                    // Android opens lazily, so force the upgrade before publishing the connection.
                    check(opened.longValue("PRAGMA user_version") == schema.version)
                    opened
                } catch (failure: Throwable) {
                    runCatching { opened.close() }.exceptionOrNull()?.let(failure::addSuppressed)
                    throw failure
                }
            }
        }
    }

    /** Closes the connection and releases the file; later use requires a new [initialize]. */
    public fun close(): Unit = runBlocking {
        lifecycle.withLock {
            driver?.close()
            driver = null
        }
    }

    internal fun requireDriver(): SqlDriver =
        driver ?: error("The Heartbeat database is not initialized; call DatabaseInitializer.initialize() first")

    private companion object {
        private const val DATABASE_NAME = "heartbeat.db"
        private const val ATTACHMENT_PREPARATION_SCHEMA = 17L
        private const val ATTACHMENT_SCHEMA = 18L
    }
}

private fun SqlDriver.longValue(sql: String): Long? = executeQuery(null, sql, { cursor ->
    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
}, 0).value

private fun SqlDriver.preserveMessagePartSequence(sequence: Long) {
    val updated = execute(null, "UPDATE sqlite_sequence SET seq = max(seq, ?) WHERE name = 'message_part'", 1) {
        bindLong(0, sequence)
    }.value
    if (updated == 0L) {
        execute(null, "INSERT INTO sqlite_sequence(name, seq) VALUES ('message_part', ?)", 1) {
            bindLong(0, sequence)
        }
    }
}
