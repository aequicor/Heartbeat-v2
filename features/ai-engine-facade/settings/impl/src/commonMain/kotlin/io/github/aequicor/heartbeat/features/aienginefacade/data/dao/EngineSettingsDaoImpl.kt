package io.github.aequicor.heartbeat.features.aienginefacade.data.dao

import app.cash.sqldelight.coroutines.asFlow
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aienginefacade.db.Acp_connection
import io.github.aequicor.heartbeat.features.aienginefacade.db.Chat_engine
import io.github.aequicor.heartbeat.features.aienginefacade.db.EngineSettingsQueries
import io.github.aequicor.heartbeat.features.aienginefacade.db.SelectEngineCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class EngineSettingsDaoImpl(
    private val database: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : EngineSettingsDao {

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun load(): EngineSettingsDao.Records = query {
        transactionWithResult {
            EngineSettingsDao.Records(selectEngines().executeAsList(), selectConnections().executeAsList())
        }
    }

    override fun observeCatalog(): Flow<List<SelectEngineCatalog>> = flow {
        val catalog = query { selectEngineCatalog() }
        emitAll(catalog.asFlow().map { query { catalog.executeAsList() } })
    }.retryWhen { failure, attempt ->
        if (failure !is EngineSettingsStorageException) return@retryWhen false
        delay(RetryIntervalMillis * (attempt + 1).coerceAtMost(MaxRetrySteps))
        true
    }.flowOn(ioDispatcher)

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun connectionFor(engineId: String): Acp_connection =
        query { selectConnectionForEngine(engineId).executeAsOne() }

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun create(connection: Acp_connection, engine: Chat_engine): Unit = query {
        require(engine.connection_id == connection.id)
        transaction {
            with(connection) {
                insertConnection(id, name, preset, command, argument_text, argument_separator, environment_json)
            }
            with(engine) { insertEngine(id, name, connection_id, local_settings_json, chat_rules) }
        }
    }

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun updateConnection(connection: Acp_connection): Unit = query {
        with(connection) {
            val updated = updateConnection(
                name, preset, command, argument_text, argument_separator, environment_json, id,
            )
            check(updated.value == 1L)
        }
    }

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun updateLocalSettings(engineId: String, settingsJson: String, rules: String): Unit = query {
        check(updateEngineSettings(settingsJson, rules, engineId).value == 1L)
    }

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun copy(
        sourceId: String,
        newId: String,
        chooseName: (String, List<String>) -> String,
    ): Chat_engine = query {
        transactionWithResult {
            val rows = selectEngineNames().executeAsList()
            val source = rows.first { it.id == sourceId }
            val name = chooseName(source.name, rows.map { it.name })
            check(copyEngine(newId, name, sourceId).value == 1L)
            selectEngineById(newId).executeAsOne()
        }
    }

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun rename(engineId: String, name: String): Unit = query {
        check(renameEngine(name, engineId).value == 1L)
    }

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    override suspend fun delete(engineId: String): Unit = query {
        deleteEngine(engineId)
    }

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    @Suppress("TooGenericExceptionCaught") // SQLDelight drivers expose platform-specific exceptions.
    private suspend fun <T> query(block: EngineSettingsQueries.() -> T): T = withContext(ioDispatcher) {
        try {
            EngineSettingsQueries(database.driver).block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw EngineSettingsStorageException(failure)
        }
    }
}

private const val RetryIntervalMillis = 1_000L
private const val MaxRetrySteps = 30L
