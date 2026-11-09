package io.github.aequicor.heartbeat.features.aienginefacade.di

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSession
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionPage
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionState
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestEngine(
    override val key: AiEngineKey,
    private val onDispose: suspend () -> Unit = {},
) : AiEngine {
    override val state = MutableStateFlow<AiEngineState>(AiEngineState.NotInitialized)
    override val sessions = MutableStateFlow<Map<AiEngineSessionKey, AiEngineSessionState>>(mapOf())
    var disposals: Int = 0
        private set

    override fun dispatch(event: AiEngineEvent) = Unit
    override suspend fun listSessions(cursor: String?): AiEngineSessionPage =
        AiEngineSessionPage(listOf())

    override suspend fun getOrCreateSession(
        config: AiEngineSessionConfig,
        existing: AiEngineSessionKey?,
    ): AiEngineSession = error("Sessions are not used in facade tests")

    override suspend fun closeSession(key: AiEngineSessionKey) = Unit

    override suspend fun dispose() {
        disposals++
        onDispose()
    }
}
