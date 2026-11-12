package io.github.aequicor.heartbeat.features.aiengineacpadapter.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AcpMethod.AgentMethods.V1
import com.agentclientprotocol.model.ListSessionsRequest
import com.agentclientprotocol.protocol.JsonRpcException
import com.agentclientprotocol.protocol.invoke
import com.agentclientprotocol.rpc.JsonRpcErrorCode
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineAuthResult
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionPage
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
internal class AcpAiEngine(
    override val key: AiEngineKey,
    private val command: List<String>,
    parentScope: CoroutineScope,
    private val projectDirectory: suspend (String) -> Path?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiEngine {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val lifecycle = Mutex()
    private var connection: AcpConnection? = null
    private var disposed = false
    private val openSessions = mutableMapOf<SessionKey, AcpSession>()
    private val engineState = MutableStateFlow<AiEngineState>(AiEngineState.NotInitialized)
    private val sessionStatuses = MutableStateFlow<Map<SessionKey, SessionStatus>>(mapOf())

    override val state = engineState.asStateFlow()
    override val sessions = sessionStatuses.asStateFlow()

    override fun dispatch(event: AiEngineEvent) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            lifecycle.withLock {
                if (!disposed) {
                    when (event) {
                        AiEngineEvent.Connect -> connect()
                        AiEngineEvent.Disconnect -> disconnect(AiEngineState.NotInitialized)
                        is AiEngineEvent.AuthorizationCompleted -> authorizationCompleted(event.result)
                    }
                }
            }
        }
    }

    override suspend fun getListSessions(config: SessionListConfig): SessionPage {
        require(config is SessionListConfig.ByProject) { "ACP cannot list child sessions" }
        val cwd = requireNotNull(config.workingDirectory?.let(Path::of) ?: projectDirectory(config.projectId)) {
            "Project has no working directory"
        }
        return request { active ->
            checkNotNull(active.agent.agentCapabilities.sessionCapabilities.list) {
                "Agent does not support listing sessions"
            }
            val page = V1.SessionList(
                active.protocol,
                ListSessionsRequest(cwd = cwd.absolute(), cursor = config.cursor),
            )
            val keys = page.sessions.map { SessionKey(key, it.sessionId.value, it.title, it.cwd, it.updatedAt) }
            SessionPage(keys, page.nextCursor)
        }
    }

    override suspend fun getOrCreateSession(config: SessionConfig, existing: SessionKey?): Session = request { active ->
        require(existing == null || existing.engine.id == key.id) { "Session belongs to another engine" }
        existing?.let(openSessions::get) ?: openSession(active, config, existing)
    }

    override suspend fun closeSession(key: SessionKey) {
        lifecycle.withLock {
            val session = openSessions.remove(key) ?: return
            val active = checkNotNull(connection)
            session.terminate(SessionStatus.Closed, cancelTurn = true)
            active.forget(session)
            sessionStatuses.update { it - key }
            withinRequestTimeout { active.closeSession(session.key.id) }
        }
    }

    override suspend fun dispose() {
        lifecycle.withLock {
            disposed = true
            disconnect(AiEngineState.NotInitialized)
            scope.cancel()
        }
    }

    @Suppress("TooGenericExceptionCaught") // Publish launch and protocol failures at the engine boundary.
    private suspend fun connect() {
        if (connection != null) return
        engineState.value = AiEngineState.Connecting
        try {
            val active = withContext(ioDispatcher) {
                val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start()
                try {
                    AcpConnection(process, scope, ioDispatcher).also { connection = it }
                } finally {
                    if (connection == null) process.terminateTree()
                }
            }
            withinRequestTimeout { active.initialize() }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    active.awaitClosed()
                    lifecycle.withLock {
                        if (connection === active) disconnect(AiEngineState.Failed("ACP agent disconnected"))
                    }
                } finally {
                    active.close()
                }
            }
            engineState.value = AiEngineState.Ready
        } catch (error: CancellationException) {
            disconnect(AiEngineState.Failed(error.failureReason()))
            currentCoroutineContext().ensureActive()
        } catch (error: Exception) {
            disconnect(AiEngineState.Failed(error.failureReason()))
        }
    }

    private fun disconnect(state: AiEngineState) {
        val status = if (state is AiEngineState.Failed) SessionStatus.Unavailable else SessionStatus.Closed
        openSessions.values.forEach { it.terminate(status) }
        openSessions.clear()
        sessionStatuses.value = mapOf()
        connection?.close()
        connection = null
        engineState.value = state
    }

    private fun authorizationCompleted(result: AiEngineAuthResult) {
        if (engineState.value !is AiEngineState.AuthorizationRequired) return
        when (result) {
            AiEngineAuthResult.Success -> engineState.value = AiEngineState.Ready
            AiEngineAuthResult.Cancelled -> disconnect(AiEngineState.NotInitialized)
            is AiEngineAuthResult.Failure -> disconnect(AiEngineState.Failed(result.reason))
        }
    }

    private suspend fun openSession(active: AcpConnection, config: SessionConfig, existing: SessionKey?): AcpSession {
        val directory = requireNotNull(config.workingDirectory ?: existing?.workingDirectory) {
            "A working directory is required to open an ACP session"
        }.let { Path.of(it).absolute() }
        val session = if (existing == null) {
            active.create(directory) { id ->
                newSession(active, SessionKey(key, id.value, workingDirectory = directory), replay = false)
            }
        } else {
            val sessionKey = SessionKey(key, existing.id, existing.title, directory, existing.updatedAt)
            val loading = newSession(active, sessionKey, replay = active.agent.agentCapabilities.loadSession)
            var opened = false
            try {
                active.reopen(loading, directory)
                opened = true
                loading
            } finally {
                if (!opened) {
                    active.forget(loading)
                    loading.terminate(SessionStatus.Closed)
                }
            }
        }
        openSessions[session.key] = session
        sessionStatuses.update { it + (session.key to session.state.value.status) }
        session.opened()
        return session
    }

    private fun newSession(active: AcpConnection, key: SessionKey, replay: Boolean): AcpSession =
        AcpSession(key, active.protocol, scope, replay) { status ->
            sessionStatuses.update { if (key in it) it + (key to status) else it }
        }

    private suspend fun <T : Any> request(block: suspend (AcpConnection) -> T): T = lifecycle.withLock {
        val active = checkNotNull(connection) { "ACP engine is disconnected" }
        check(engineState.value == AiEngineState.Ready) { "ACP engine is not ready" }
        try {
            withinRequestTimeout { block(active) }
        } catch (error: JsonRpcException) {
            if (error.code == JsonRpcErrorCode.AUTH_REQUIRED.code) {
                val auth = active.agent.authMethods.firstOrNull()?.id?.value
                engineState.value = if (auth == null) {
                    AiEngineState.Failed(error.message)
                } else {
                    AiEngineState.AuthorizationRequired(auth)
                }
                if (auth == null) disconnect(engineState.value)
            }
            throw error
        }
    }
}

internal suspend fun <T : Any> withinRequestTimeout(block: suspend () -> T): T =
    withTimeoutOrNull(15.seconds) { block() } ?: error("ACP request timed out")

internal fun Throwable.failureReason(): String = message ?: javaClass.simpleName

private fun Path.absolute(): String {
    require(isAbsolute) { "ACP requires an absolute working directory" }
    return normalize().toString()
}
