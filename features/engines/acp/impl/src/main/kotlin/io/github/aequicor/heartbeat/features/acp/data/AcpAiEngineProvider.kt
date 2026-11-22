package io.github.aequicor.heartbeat.features.acp.data

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.acp.api.AcpProviderFactory
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/** Creates a runtime that resolves its launch parameters when it connects. */
internal class AcpAiEngineProvider(
    override val key: AiEngineKey,
    private val launch: suspend () -> AcpProviderFactory.Launch,
    private val scope: CoroutineScope,
    private val projectDirectory: suspend (projectId: String) -> Path?,
    private val currentKey: () -> AiEngineKey = { key },
    private val ungroupedDirectory: (() -> Path)? = null,
) : AiEngineProvider {

    init {
        require(key.connection == AiEngineConnection.ACP) { "An ACP engine key is required" }
    }

    override fun create(): AiEngine = AcpAiEngine(currentKey, launch, scope, projectDirectory, ungroupedDirectory)
}
