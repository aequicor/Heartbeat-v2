package io.github.aequicor.heartbeat.features.aienginefacade.di

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionPage
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestEngine(
    override val key: AiEngineKey,
    private val onDispose: suspend () -> Unit = {},
) : AiEngine {
    override val state = MutableStateFlow<AiEngineState>(AiEngineState.NotInitialized)
    override val sessions = MutableStateFlow<Map<SessionKey, SessionStatus>>(mapOf())
    var disposals: Int = 0
        private set

    override fun dispatch(event: AiEngineEvent) = Unit
    override suspend fun getListSessions(config: SessionListConfig): SessionPage =
        SessionPage(listOf())

    override suspend fun getOrCreateSession(
        config: SessionConfig,
        existing: SessionKey?,
    ): Session = error("Sessions are not used in facade tests")

    override suspend fun closeSession(key: SessionKey) = Unit

    override suspend fun dispose() {
        disposals++
        onDispose()
    }
}
