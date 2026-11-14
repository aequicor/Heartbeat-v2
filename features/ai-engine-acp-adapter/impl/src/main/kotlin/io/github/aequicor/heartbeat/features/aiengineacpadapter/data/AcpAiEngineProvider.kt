package io.github.aequicor.heartbeat.features.aiengineacpadapter.data

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/** Provides an ACP engine for an installed agent. [command] contains the executable and its arguments. */
internal class AcpAiEngineProvider(
    override val key: AiEngineKey,
    command: List<String>,
    private val scope: CoroutineScope,
    private val projectDirectory: suspend (projectId: String) -> Path?,
) : AiEngineProvider {
    private val command = command.toList()

    init {
        require(key.connection == AiEngineConnection.ACP) { "An ACP engine key is required" }
        require(this.command.firstOrNull()?.isNotBlank() == true) { "An agent executable is required" }
    }

    override fun create(): AiEngine = AcpAiEngine(key, command, scope, projectDirectory)
}
