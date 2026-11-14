package io.github.aequicor.heartbeat.features.aiengineacpadapter.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import io.github.aequicor.heartbeat.features.aiengineacpadapter.data.AcpAiEngineProvider
import java.nio.file.Path

/** Creates an engine provider for an installed ACP agent and its project directory resolver. */
@Inject
public class AcpAgentDataSource(private val scope: AppCoroutineScope) {
    public fun provider(
        key: AiEngineKey,
        command: List<String>,
        projectDirectory: suspend (projectId: String) -> Path? = { null },
    ): AiEngineProvider = AcpAiEngineProvider(key, command, scope, projectDirectory)
}
