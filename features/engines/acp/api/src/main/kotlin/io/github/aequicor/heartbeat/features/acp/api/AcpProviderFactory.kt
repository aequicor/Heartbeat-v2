package io.github.aequicor.heartbeat.features.acp.api

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider

public fun interface AcpProviderFactory {

    /** Resolve the latest engine metadata and the saved command for each new process. */
    public fun create(
        key: AiEngineKey,
        currentKey: () -> AiEngineKey,
        launch: suspend () -> Launch,
    ): AiEngineProvider

    public data class Launch(val command: List<String>, val environment: Map<String, String>)
}
