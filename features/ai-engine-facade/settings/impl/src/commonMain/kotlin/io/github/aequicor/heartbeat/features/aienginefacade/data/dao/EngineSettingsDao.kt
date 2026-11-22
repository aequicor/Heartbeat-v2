package io.github.aequicor.heartbeat.features.aienginefacade.data.dao

import io.github.aequicor.heartbeat.features.aienginefacade.db.Acp_connection
import io.github.aequicor.heartbeat.features.aienginefacade.db.Chat_engine
import io.github.aequicor.heartbeat.features.aienginefacade.db.SelectEngineCatalog
import kotlinx.coroutines.flow.Flow
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface EngineSettingsDao {

    data class Records(val engines: List<Chat_engine>, val connections: List<Acp_connection>)

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun load(): Records

    fun observeCatalog(): Flow<List<SelectEngineCatalog>>

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun connectionFor(engineId: String): Acp_connection

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun create(connection: Acp_connection, engine: Chat_engine)

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun updateConnection(connection: Acp_connection)

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun updateLocalSettings(engineId: String, settingsJson: String, rules: String)

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun copy(sourceId: String, newId: String, chooseName: (String, List<String>) -> String): Chat_engine

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun rename(engineId: String, name: String)

    @Throwable(EngineSettingsStorageException::class, CancellationException::class)
    suspend fun delete(engineId: String)
}
