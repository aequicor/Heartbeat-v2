package io.github.aequicor.heartbeat.features.aienginefacade.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aienginefacade.data.dao.EngineSettingsDao
import io.github.aequicor.heartbeat.features.aienginefacade.data.dao.EngineSettingsStorageException
import io.github.aequicor.heartbeat.features.aienginefacade.data.entity.toDomain
import io.github.aequicor.heartbeat.features.aienginefacade.data.entity.toEntity
import io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource.EngineSettingsDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.AcpConnectionSettings
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.ChatEngine
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class EngineSettingsDataSourceImpl(private val dao: EngineSettingsDao) : EngineSettingsDataSource {

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    override suspend fun load(): EngineSettingsDataSource.Catalog = storage {
        val rows = dao.load()
        EngineSettingsDataSource.Catalog(
            rows.engines.map { it.toDomain() },
            rows.connections.associate { it.id to it.toDomain() },
        )
    }

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    override suspend fun create(connection: AcpConnectionSettings, engine: ChatEngine) =
        storage { dao.create(connection.toEntity(), engine.toEntity()) }

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    override suspend fun update(connection: AcpConnectionSettings) = storage {
        dao.updateConnection(connection.toEntity())
    }

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    override suspend fun update(engine: ChatEngine) = storage {
        val record = engine.toEntity()
        dao.updateLocalSettings(record.id, record.local_settings_json, record.chat_rules)
    }

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    override suspend fun copy(id: String, newId: String, chooseName: (String, List<String>) -> String): ChatEngine =
        storage { dao.copy(id, newId, chooseName).toDomain() }

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    override suspend fun rename(id: String, name: String) = storage { dao.rename(id, name) }

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    override suspend fun delete(id: String) = storage { dao.delete(id) }

    @Throwable(EngineSettingsDataSource.Failure::class, CancellationException::class)
    private suspend fun <T> storage(block: suspend () -> T): T = try {
        block()
    } catch (failure: EngineSettingsStorageException) {
        throw EngineSettingsDataSource.Failure(failure)
    } catch (failure: IllegalArgumentException) {
        throw EngineSettingsDataSource.Failure(failure)
    }
}
