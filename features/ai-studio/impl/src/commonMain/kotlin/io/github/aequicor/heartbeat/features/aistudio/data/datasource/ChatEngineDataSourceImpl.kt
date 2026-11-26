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
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSecurityMode
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.Throws as Throwable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private data class UsageObserver(val session: Session, val job: Job, val securityModeLock: Mutex = Mutex())

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
            override val settingsUpdates = session.state.map { it.settings.toDomain() }.distinctUntilChanged()

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
        return ChatEngineConversationDataSourceImpl(
            session, attachments, validateSecurityMode = { validateSecurityMode(chatSessionId, session) },
        ) { engine.closeSession(session.key) }
    }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun selectSecurityMode(chatId: String, value: String?, directory: String?) {
        val chat = conversations.find(chatId) ?: throw ChatTurnException("Чат недоступен")
        val engineId = chat.engineId ?: throw ChatTurnException("Движок недоступен")
        val sessionId = chat.sessionId ?: throw ChatTurnException("Сессия движка недоступна")
        var session = observerLock.withLock { observers[chatId]?.session }?.takeIf {
            it.key.id == sessionId && it.key.engine.id == engineId &&
                it.state.value.status != SessionStatus.Closed && it.state.value.status != SessionStatus.Unavailable
        }
        val status = session?.state?.value?.status
        if (status != null && status != SessionStatus.Idle && status !is SessionStatus.Failed) {
            throw ChatTurnException("Дождитесь завершения ответа")
        }
        if (value != null) {
            val saved = conversations.updateSecurityMode(chatId, engineId, sessionId) { current ->
                if (current?.options?.any { it.id == value } == true) current.choose(value) else current
            }
            if (saved?.pendingValue != value) throw ChatTurnException("Список режимов изменился")
            if (session == null) return
        } else {
            val engine = connect(engineId)
            if (session != null && session.state.value.settings.securityMode?.choices.isNullOrEmpty()) {
                engine.closeSession(session.key)
                session = null
            }
            if (session == null) {
                session = engine.getOrCreateSession(SessionConfig(directory), SessionKey(engine.key, sessionId, workingDirectory = directory))
                observeUsage(chatId, session)
            }
        }
        validateSecurityMode(chatId, checkNotNull(session))
    }

    private suspend fun validateSecurityMode(chatId: String, session: Session): SessionSettings {
        val observer = observerLock.withLock { observers[chatId] }?.takeIf { it.session === session }
            ?: throw ChatTurnException("Сессия движка недоступна", notDispatched = true)
        return observer.securityModeLock.withLock { applyPendingSecurityMode(chatId, session) }
    }

    private suspend fun applyPendingSecurityMode(chatId: String, session: Session): SessionSettings {
        val snapshot = session.state.value.settings
        val securityMode = reconcile(chatId, session, snapshot)
        if (securityMode?.requiresUserChoice == true) throw ChatTurnException("Выберите доступный режим агента", notDispatched = true)
        val pending = securityMode?.pendingValue ?: return snapshot
        val applied = session.selectSetting(securityMode.configId, pending)
        val updated = reconcile(chatId, session, applied)
        if (updated?.confirmedValue != pending || updated.pendingValue != null || updated.requiresUserChoice) {
            throw ChatTurnException("Агент не подтвердил выбранный режим", notDispatched = true)
        }
        return applied
    }

    private suspend fun reconcile(chatId: String, session: Session, settings: SessionSettings): ChatSecurityMode? {
        val securityMode = settings.securityMode?.toDomain()
        return conversations.updateSecurityMode(chatId, session.key.engine.id, session.key.id) { ChatSecurityMode.reconcile(it, securityMode) }
    }

    private suspend fun observeUsage(chatId: String, session: Session): Unit = observerLock.withLock {
        val previous = observers[chatId]
        if (previous?.session === session) return@withLock
        previous?.job?.cancel()
        val job = appScope.launch {
            var savedSettings: SessionSettings? = null
            var saved: ContextWindowUsage? = null
            session.state.onEach { state ->
                if (state.settings != savedSettings && state.status != SessionStatus.Closed && state.status != SessionStatus.Unavailable) {
                    val observer = observerLock.withLock { observers[chatId] }
                    if (observer?.session === session) observer.securityModeLock.withLock {
                        val latest = session.state.value.settings
                        reconcile(chatId, session, latest)
                        savedSettings = latest
                    }
                }
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
    securityMode?.toDomain(),
)

private fun SessionSettings.Selector.toDomain() = ChatEngineOptions.Selector(
    id, current, choices.map { ChatEngineOptions.Choice(it.id, it.name, it.description, it.group, it.groupName) }, currentConfirmed,
)
