package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSettings
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PreparedChatSession
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@Inject
internal class ChatEngineDataSourceImpl(private val facade: AiEngineFacade) : ChatEngineDataSource {

    override val engines = facade.availableEngines.map { keys -> keys.map { ChatEngineOptions.Choice(it.id, it.name) } }

    override suspend fun open(projectId: ProjectId, engineId: String, directory: String?): PreparedChatSession {
        val engine = connect(engineId)
        val session = engine.getOrCreateSession(SessionConfig(directory))
        return object : PreparedChatSession {
            override val projectId = projectId
            override val engineId = engine.key.id
            override val sessionId = session.key.id
            override val isAvailable get() = facade.availableEngines.value.any { it.id == engineId } &&
                session.state.value.status == SessionStatus.Idle
            override val settings get() = session.state.value.settings.toDomain()

            override suspend fun select(selector: String, value: String) = session.selectSetting(selector, value).toDomain()

            override suspend fun close() = engine.closeSession(session.key)
        }
    }

    override suspend fun resume(engineId: String, sessionId: String, directory: String?): ChatEngineConversationDataSource {
        val engine = connect(engineId)
        val key = SessionKey(engine.key, sessionId, workingDirectory = directory)
        val session = engine.getOrCreateSession(SessionConfig(directory), existing = key)
        check(session.key == key) { "The engine returned another session" }
        return ChatEngineConversationDataSourceImpl(session)
    }

    private suspend fun connect(engineId: String): AiEngine {
        val key = facade.availableEngines.value.first { it.id == engineId }
        val engine = facade.getEngine(key)
        if (engine.state.value != AiEngineState.Ready) {
            engine.dispatch(AiEngineEvent.Connect)
            val state = withTimeoutOrNull(20.seconds) {
                engine.state.first { it != AiEngineState.Connecting && it != AiEngineState.NotInitialized }
            }
            check(state == AiEngineState.Ready) { "Engine did not connect" }
        }
        return engine
    }
}

internal fun SessionSettings.toDomain() = ChatEngineOptions(model?.toDomain(), reasoning?.toDomain())

private fun SessionSettings.Selector.toDomain() = ChatEngineOptions.Selector(
    id, current, choices.map { ChatEngineOptions.Choice(it.id, it.name) },
)
