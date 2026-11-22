package io.github.aequicor.heartbeat.features.aienginefacade.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource.AcpConnectionCheckDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource.EngineSettingsDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.AcpConnectionSettings
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.ChatEngine
import io.github.aequicor.heartbeat.features.aienginefacade.domain.util.rethrowCancellation
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

@Inject
internal class EngineSettingsInteractorImpl(
    private val data: EngineSettingsDataSource,
    private val probe: AcpConnectionCheckDataSource,
) : EngineSettingsInteractor {

    @Throwable(CancellationException::class)
    override suspend fun load(): Result<EngineSettingsDataSource.Catalog> = runCatching {
        data.load()
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun create(connection: AcpConnectionSettings): Result<ChatEngine> = runCatching {
        require(connection.name.isNotBlank() && connection.launchArguments() != null)
        val engine = ChatEngine(id = connection.id, name = connection.name, connectionId = connection.id)
        data.create(connection, engine)
        engine
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun save(connection: AcpConnectionSettings): Result<Unit> = runCatching {
        require(connection.name.isNotBlank() && connection.launchArguments() != null)
        data.update(connection)
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun save(engine: ChatEngine): Result<Unit> = runCatching {
        data.update(engine)
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun copy(id: String): Result<ChatEngine> = runCatching {
        data.copy(id, Uuid.random().toString()) { original, names ->
            val stem = "$original — копия"
            if (stem !in names) stem else generateSequence(2) { it + 1 }.map { "$stem $it" }.first { it !in names }
        }
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun rename(id: String, name: String): Result<Unit> = runCatching {
        require(name.isNotBlank())
        data.rename(id, name)
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun delete(id: String): Result<Unit> = runCatching {
        data.delete(id)
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun check(connection: AcpConnectionSettings): Result<Int> = runCatching {
        val arguments = requireNotNull(connection.launchArguments()) { "Проверьте команду, аргументы и окружение" }
        probe.initialize(connection.command, arguments, connection.environment)
    }.rethrowCancellation()
}
