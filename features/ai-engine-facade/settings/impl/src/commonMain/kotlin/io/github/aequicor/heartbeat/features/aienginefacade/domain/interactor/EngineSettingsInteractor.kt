package io.github.aequicor.heartbeat.features.aienginefacade.domain.interactor

import io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource.EngineSettingsDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.AcpConnectionSettings
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.ChatEngine
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface EngineSettingsInteractor {

    @Throwable(CancellationException::class)
    suspend fun load(): Result<EngineSettingsDataSource.Catalog>

    @Throwable(CancellationException::class)
    suspend fun create(connection: AcpConnectionSettings): Result<ChatEngine>

    @Throwable(CancellationException::class)
    suspend fun save(connection: AcpConnectionSettings): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun save(engine: ChatEngine): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun copy(id: String): Result<ChatEngine>

    @Throwable(CancellationException::class)
    suspend fun rename(id: String, name: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun delete(id: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun check(connection: AcpConnectionSettings): Result<Int>
}
