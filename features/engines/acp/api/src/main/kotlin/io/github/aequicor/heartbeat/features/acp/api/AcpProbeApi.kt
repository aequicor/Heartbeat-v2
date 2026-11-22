package io.github.aequicor.heartbeat.features.acp.api

import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

public fun interface AcpProbeApi {

    /** Starts a short-lived stdio agent and returns the version from its initialize response. */
    @Throwable(AcpProbeException::class, CancellationException::class)
    public suspend fun initialize(command: String, arguments: List<String>, environment: Map<String, String>): Int
}
