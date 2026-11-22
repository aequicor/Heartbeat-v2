package io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource

import io.github.aequicor.heartbeat.features.aienginefacade.domain.exception.AcpConnectionCheckException
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface AcpConnectionCheckDataSource {

    @Throwable(AcpConnectionCheckException::class, CancellationException::class)
    suspend fun initialize(command: String, arguments: List<String>, environment: Map<String, String>): Int
}
