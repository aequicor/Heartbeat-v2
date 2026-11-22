package io.github.aequicor.heartbeat.features.acp.data.datasource

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.acp.api.AcpProviderFactory
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import io.github.aequicor.heartbeat.features.acp.data.AcpAiEngineProvider
import java.nio.file.Path

/** Creates an engine provider for an installed ACP agent and its project directory resolver. */
@ContributesBinding(AppScope::class)
public class AcpAgentDataSource(
    private val scope: AppCoroutineScope,
    private val dataDirectory: String,
) : AcpProviderFactory {

    override fun create(
        key: AiEngineKey,
        currentKey: () -> AiEngineKey,
        launch: suspend () -> AcpProviderFactory.Launch,
    ): AiEngineProvider = AcpAiEngineProvider(
        key, launch, scope, projectDirectory = { null }, currentKey = currentKey,
        ungroupedDirectory = { java.nio.file.Files.createDirectories(Path.of(dataDirectory, "chats")) },
    )

    public fun provider(
        key: AiEngineKey,
        command: List<String>,
        projectDirectory: suspend (projectId: String) -> Path? = { null },
    ): AiEngineProvider {
        require(command.firstOrNull()?.isNotBlank() == true) { "An agent executable is required" }
        val launch = AcpProviderFactory.Launch(command.toList(), mapOf())
        return AcpAiEngineProvider(key, { launch }, scope, projectDirectory)
    }
}
