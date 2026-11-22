package io.github.aequicor.heartbeat.features.aienginefacade.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.acp.api.AcpProbeApi
import io.github.aequicor.heartbeat.features.acp.api.AcpProbeException
import io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource.AcpConnectionCheckDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.exception.AcpConnectionCheckException
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class AcpConnectionCheckDataSourceImpl(private val api: AcpProbeApi) : AcpConnectionCheckDataSource {

    @Throwable(AcpConnectionCheckException::class, CancellationException::class)
    override suspend fun initialize(command: String, arguments: List<String>, environment: Map<String, String>): Int =
        try {
            api.initialize(command, arguments, environment)
        } catch (failure: AcpProbeException) {
            throw AcpConnectionCheckException(failure.started, failure)
        }
}
