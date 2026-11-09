package io.github.aequicor.heartbeat.ai.engines.deepseekharness

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.ai.engine.api.AiEngine
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineProvider
import io.github.aequicor.heartbeat.ai.engine.api.EngineKey
import io.github.aequicor.heartbeat.ai.engine.acp.AcpAdapter
import io.github.aequicor.heartbeat.di.AppScope

/**
 * Registers DeepSeek Harness in the AI engine catalog.
 *
 * The module contains no agent logic: the engine is the shared ACP adapter, and the only difference
 * from another ACP agent is the launch configuration. The configuration is resolved when the engine
 * connects, not when the provider creates it.
 */
@ContributesIntoSet(AppScope::class)
public class DeepSeekHarnessProvider @Inject constructor(
    private val settings: DeepSeekLaunchSettings,
) : AiEngineProvider {

    override val key: EngineKey = DeepSeekHarness.key

    override fun create(): AiEngine = AcpAdapter(key, ::launchSpec)

    internal fun launchSpec() = settings.current().toAcpLaunchSpec()
}
