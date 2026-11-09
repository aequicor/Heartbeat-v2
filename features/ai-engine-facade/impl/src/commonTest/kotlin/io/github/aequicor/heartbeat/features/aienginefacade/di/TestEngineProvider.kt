package io.github.aequicor.heartbeat.features.aienginefacade.di

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider

internal class TestEngineProvider(
    override val key: AiEngineKey,
    private val createEngine: (AiEngineKey) -> TestEngine = { TestEngine(it) },
) : AiEngineProvider {
    var creations: Int = 0
        private set

    override fun create(): AiEngine {
        creations++
        return createEngine(key)
    }
}
