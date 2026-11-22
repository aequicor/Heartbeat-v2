package io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource

import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.AcpConnectionSettings
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.ChatEngine
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface EngineSettingsDataSource {

    data class Catalog(val engines: List<ChatEngine>, val connections: Map<String, AcpConnectionSettings>)

    class Failure(cause: kotlin.Throwable) : Exception("Не удалось прочитать или изменить настройки движка", cause)

    @Throwable(Failure::class, CancellationException::class)
    suspend fun load(): Catalog

    @Throwable(Failure::class, CancellationException::class)
    suspend fun create(connection: AcpConnectionSettings, engine: ChatEngine): Unit

    @Throwable(Failure::class, CancellationException::class)
    suspend fun update(connection: AcpConnectionSettings): Unit

    @Throwable(Failure::class, CancellationException::class)
    suspend fun update(engine: ChatEngine): Unit

    @Throwable(Failure::class, CancellationException::class)
    suspend fun copy(id: String, newId: String, chooseName: (String, List<String>) -> String): ChatEngine

    @Throwable(Failure::class, CancellationException::class)
    suspend fun rename(id: String, name: String): Unit

    @Throwable(Failure::class, CancellationException::class)
    suspend fun delete(id: String): Unit
}
