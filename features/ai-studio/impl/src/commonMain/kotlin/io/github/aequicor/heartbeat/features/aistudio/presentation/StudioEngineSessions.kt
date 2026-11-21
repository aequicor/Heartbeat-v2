package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.data.StudioCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

internal class StudioEngineSessions(private val facade: AiEngineFacade, private val catalog: StudioCatalog) {
    private var observer: Job? = null
    private val imports = mutableMapOf<Pair<String, String>, Job>()
    var selected: Session? = null
        private set

    fun clearSelection() {
        observer?.cancel()
        selected = null
    }

    fun select(opened: Opened, scope: CoroutineScope, onState: (Session, SessionState) -> Unit) {
        observer?.cancel()
        selected = opened.session
        observer = scope.launch { opened.session.state.collect { onState(opened.session, it) } }
        val key = opened.projectId to opened.engine.key.id
        if (imports[key]?.isActive == true) return
        imports[key] = scope.launch {
            try {
                importSessions(opened)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Listing is optional. A failed page never invalidates known sessions.
            }
        }
    }

    val availableEngines get() = facade.availableEngines

    fun defaultEngineId(): String? = facade.availableEngines.value.singleOrNull()?.id

    suspend fun open(projectId: String, engineId: String, nativeId: String?): Opened {
        val directory = requireNotNull(catalog.directory(projectId)) { "Для проекта нужна рабочая папка" }
        val key = requireNotNull(facade.availableEngines.value.find { it.id == engineId }) {
            "Движок этой сессии недоступен"
        }
        val engine = facade.getEngine(key)
        if (engine.state.value != AiEngineState.Ready) {
            if (engine.state.value !is AiEngineState.AuthorizationRequired) engine.dispatch(AiEngineEvent.Connect)
            val state = withTimeoutOrNull(ConnectTimeout) {
                engine.state.first {
                    it == AiEngineState.Ready || it is AiEngineState.Failed || it is AiEngineState.AuthorizationRequired
                }
            }
            check(state == AiEngineState.Ready) {
                when (state) {
                    is AiEngineState.AuthorizationRequired -> "Требуется авторизация движка"
                    is AiEngineState.Failed -> state.reason
                    else -> "Не удалось подключить движок"
                }
            }
        }
        val existing = nativeId?.let { SessionKey(key, it, workingDirectory = directory) }
        val session = engine.getOrCreateSession(SessionConfig(directory), existing)
        val localId = catalog.recordNative(projectId, session.key, created = existing == null)
        return Opened(localId, projectId, directory, engine, session)
    }

    /** Import only through the engine that has already opened a working session. */
    private suspend fun importSessions(opened: Opened) {
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val config = SessionListConfig.ByProject(opened.projectId, cursor, opened.directory)
            val page = opened.engine.getListSessions(config)
            catalog.transaction {
                page.sessions.forEach { catalog.recordNative(opened.projectId, it) }
            }
            cursor = page.nextCursor
        } while (cursor != null && seen.add(cursor))
    }

    data class Opened(
        val localId: String, val projectId: String, val directory: String, val engine: AiEngine, val session: Session,
    )

    private companion object {
        val ConnectTimeout = 20.seconds
    }
}
