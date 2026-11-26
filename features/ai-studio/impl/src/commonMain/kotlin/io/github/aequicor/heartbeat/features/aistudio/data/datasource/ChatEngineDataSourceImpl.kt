package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.ContextWindowUsage
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSettings
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatConversationDao
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PreparedChatSession
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
internal class ChatEngineDataSourceImpl(
    private val facade: AiEngineFacade,
    private val attachments: ChatAttachmentDataSource,
    private val conversations: ChatConversationDao,
    private val appScope: AppCoroutineScope,
) : ChatEngineDataSource {

    private val observerLock = Mutex()
    private val observers = mutableMapOf<String, UsageObserver>()

    private data class UsageObserver(val session: Session, val job: Job)

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

            override suspend fun trackContextUsage(chatSessionId: String) = observeUsage(chatSessionId, session)

            override suspend fun select(selector: String, value: String) = session.selectSetting(selector, value).toDomain()

            override suspend fun close() = engine.closeSession(session.key)
        }
    }

    override suspend fun resume(
        chatSessionId: String, engineId: String, sessionId: String, directory: String?,
    ): ChatEngineConversationDataSource {
        val engine = connect(engineId)
        val key = SessionKey(engine.key, sessionId, workingDirectory = directory)
        val session = engine.getOrCreateSession(SessionConfig(directory), existing = key)
        check(session.key == key) { "The engine returned another session" }
        observeUsage(chatSessionId, session)
        return ChatEngineConversationDataSourceImpl(session, attachments) { engine.closeSession(session.key) }
    }

    private suspend fun observeUsage(chatId: String, session: Session): Unit = observerLock.withLock {
        val previous = observers[chatId]
        if (previous?.session === session) return@withLock
        previous?.job?.cancel()
        val job = appScope.launch {
            var saved: ContextWindowUsage? = null
            session.state.onEach { state ->
                val usage = state.contextWindowUsage
                if (usage != null && usage != saved) observerLock.withLock {
                    if (observers[chatId]?.session === session) {
                        conversations.saveContextUsage(chatId, session.key, usage)
                        saved = usage
                    }
                }
            }.retryWhen { failure, _ ->
                if (failure !is DaoException) return@retryWhen false
                delay(1.seconds)
                true
            }.first { it.status == SessionStatus.Closed || it.status == SessionStatus.Unavailable }
            observerLock.withLock {
                if (observers[chatId]?.session === session) observers.remove(chatId)
            }
        }
        observers[chatId] = UsageObserver(session, job)
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

internal fun SessionSettings.toDomain() = ChatEngineOptions(
    model?.toDomain(),
    reasoning?.takeIf { it.choices.isNotEmpty() }?.toDomain(),
)

private fun SessionSettings.Selector.toDomain() = ChatEngineOptions.Selector(
    id, current, choices.map { ChatEngineOptions.Choice(it.id, it.name) },
)
