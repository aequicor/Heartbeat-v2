package io.github.aequicor.heartbeat.storage.database

import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope

/**
 * [DatabaseConnection] that fails until [DatabaseInitializer] has opened the aggregate schema.
 *
 * Feature data sources depend on this contract, so Metro can construct them in the platform graph
 * while the actual connection still belongs to the infrastructure owner.
 */
@ContributesBinding(AppScope::class)
public class DefaultDatabaseConnection @Inject constructor(
    private val initializer: DatabaseInitializer,
) : DatabaseConnection {

    override val driver: SqlDriver
        get() = initializer.requireDriver()
}
