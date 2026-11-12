package io.github.aequicor.heartbeat.ai.engine.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionInfo
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.aequicor.heartbeat.ai.engine.api.AiEngine
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineException
import io.github.aequicor.heartbeat.ai.engine.api.AiSession
import io.github.aequicor.heartbeat.ai.engine.api.AuthResult
import io.github.aequicor.heartbeat.ai.engine.api.EngineCapabilities
import io.github.aequicor.heartbeat.ai.engine.api.EngineCapability
import io.github.aequicor.heartbeat.ai.engine.api.EngineEvent
import io.github.aequicor.heartbeat.ai.engine.api.EngineKey
import io.github.aequicor.heartbeat.ai.engine.api.EngineState
import io.github.aequicor.heartbeat.ai.engine.api.SessionConfig
import io.github.aequicor.heartbeat.ai.engine.api.SessionKey
import io.github.aequicor.heartbeat.ai.engine.api.SessionPage
import io.github.aequicor.heartbeat.ai.engine.api.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/**
 * ACP implementation of [AiEngine].
 *
 * The adapter owns the child process, the ACP client connection and the runtime sessions. It
 * derives optional capabilities from the `initialize` answer and never assumes what the agent can
 * do from its key.
 *
 * Dispatch calls are not suspending: connecting performs I/O in the engine scope and reports the
 * result through [state]. A synchronous misuse, such as a prompt on a busy session, throws
 * [AiEngineException].
 */
// The AiEngine contract fixes the surface; the private helpers are cohesive. The adapter owns its
// I/O dispatcher because the platform graph provides no dispatcher to a lazily created engine.
@Suppress("TooManyFunctions", "InjectDispatcher")
public class AcpAdapter internal constructor(
    override val key: EngineKey,
    private val resolveLaunch: () -> AcpLaunchSpec,
    private val processStarter: AcpProcessStarter,
) : AiEngine {

    public constructor(
        key: EngineKey,
        resolveLaunch: () -> AcpLaunchSpec,
    ) : this(key, resolveLaunch, AcpProcessLauncher)

    private val engineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("acp-${key.id}"),
    )

    private val mutableState = MutableStateFlow<EngineState>(EngineState.NotInitialized)
    override val state: StateFlow<EngineState> = mutableState.asStateFlow()

    private val mutableCapabilities = MutableStateFlow(EngineCapabilities())
    override val capabilities: StateFlow<EngineCapabilities> = mutableCapabilities.asStateFlow()

    private val mutableSessions = MutableStateFlow<List<SessionKey>>(listOf())
    override val sessions: StateFlow<List<SessionKey>> = mutableSessions.asStateFlow()

    private val sessionLock = Any()
    private val sessionsByKey = LinkedHashMap<SessionKey, AcpSession>()

    @Volatile
    private var connection: Connection? = null
    private var connectJob: Job? = null

    @Volatile
    private var connectToken: Any? = null
    private var activeLaunch: AcpLaunchSpec? = null

    @Volatile
    private var pendingAuthMethodId: String? = null

    @Volatile
    private var transportFailure: String? = null

    private class Connection(
        val stdio: AcpStdio,
        val protocol: Protocol,
        val client: Client,
    )

    override fun dispatch(event: EngineEvent) {
        when (event) {
            EngineEvent.Connect -> connect()
            EngineEvent.Disconnect -> disconnect()
            is EngineEvent.AuthorizationCompleted -> onAuthorizationCompleted(event.result)
        }
    }

    @OptIn(UnstableApi::class)
    override suspend fun listSessions(cursor: String?): SessionPage {
        val active = readyConnection()
        requireCapability(EngineCapability.SESSION_LIST)
        val infos: List<SessionInfo> = active.client.listSessions(cwd = null).toList()
        return SessionPage(
            sessions = infos.map { info ->
                SessionKey(
                    engine = key,
                    id = info.sessionId.value,
                    title = info.title,
                    workingDirectory = info.cwd,
                    updatedAt = info.updatedAt,
                )
            },
            nextCursor = null,
        )
    }

    @OptIn(UnstableApi::class)
    override suspend fun createSession(
        config: SessionConfig,
        existing: SessionKey?,
    ): AiSession {
        val active = readyConnection()
        require(existing == null || existing.engine.id == key.id) {
            "Session ${existing?.id} belongs to another engine"
        }
        return if (existing == null) {
            startNewSession(active, config)
        } else {
            openExistingSession(active, existing, config)
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun openExistingSession(
        active: Connection,
        existing: SessionKey,
        config: SessionConfig,
    ): AiSession {
        val open = synchronized(sessionLock) { sessionsByKey[existing] }
        if (open != null) return open
        requireCapability(EngineCapability.SESSION_RESUME)
        val workingDirectory = existing.workingDirectory
            ?: config.workingDirectory
            ?: defaultWorkingDirectory()
        val core = SessionCore(engineScope)
        val session = AcpSession(existing.withMetadata(workingDirectory = workingDirectory), core)
        val clientSession = active.client.resumeSession(
            SessionId(existing.id),
            parameters(workingDirectory),
            core,
        )
        core.attach(clientSession)
        register(session)
        return session
    }

    @OptIn(UnstableApi::class)
    private suspend fun startNewSession(
        active: Connection,
        config: SessionConfig,
    ): AiSession {
        val workingDirectory = config.workingDirectory ?: defaultWorkingDirectory()
        val core = SessionCore(engineScope)
        val clientSession = active.client.newSession(parameters(workingDirectory)) { _, _ -> core }
        val session = AcpSession(
            key = SessionKey(
                engine = key,
                id = clientSession.sessionId.value,
                workingDirectory = workingDirectory,
            ),
            core = core,
        )
        core.attach(clientSession)
        register(session)
        return session
    }

    override fun getSession(key: SessionKey): AiSession? =
        synchronized(sessionLock) { sessionsByKey[key] }

    @OptIn(UnstableApi::class)
    override suspend fun closeSession(key: SessionKey) {
        val session = synchronized(sessionLock) {
            sessionsByKey.remove(key)?.also { publishSessionsLocked() }
        } ?: return
        session.shutdown(SessionState.Closed)
        if (capabilities.value.supports(EngineCapability.SESSION_CLOSE)) {
            runCatching { connection?.client?.getSession(SessionId(key.id))?.close() }
        }
    }

    override suspend fun dispose() {
        connectToken = null
        connectJob?.cancel()
        connectJob = null
        val active = connection
        connection = null
        pendingAuthMethodId = null
        closeAllSessions(SessionState.Closed)
        mutableState.value = EngineState.NotInitialized
        mutableCapabilities.value = EngineCapabilities()
        if (active != null) {
            closeConnection(active)
        }
        engineScope.cancel()
    }

    private fun connect() {
        synchronized(this) {
            when (mutableState.value) {
                is EngineState.Connecting, is EngineState.Ready -> return
                else -> Unit
            }
            val token = Any()
            connectToken = token
            transportFailure = null
            mutableState.value = EngineState.Connecting
            connectJob = engineScope.launch { establish(token) }
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    // Early exits separate launch resolution, process start and a connection cancelled mid-handshake.
    private suspend fun establish(token: Any) {
        val spec = try {
            resolveLaunch()
        } catch (error: Exception) {
            failConnect(token, "Cannot resolve the launch configuration: ${error.message}")
            return
        }
        activeLaunch = spec
        val stdio = try {
            processStarter.start(spec, engineScope)
        } catch (error: Exception) {
            failConnect(token, "Cannot start ${spec.executable}: ${error.message}")
            return
        }
        try {
            val transport = StdioTransport(
                parentScope = engineScope,
                ioDispatcher = Dispatchers.IO,
                input = stdio.input,
                output = stdio::write,
                name = "acp-${key.id}",
            )
            val protocol = Protocol(engineScope, transport)
            val client = Client(protocol)
            protocol.start()
            val info = client.initialize(
                ClientInfo(
                    implementation = Implementation(name = "Heartbeat", version = "2.0"),
                ),
            )
            if (connectToken !== token) {
                stdio.terminate()
                return
            }
            mutableCapabilities.value = info.capabilities.toEngineCapabilities()
            val active = Connection(stdio, protocol, client)
            connection = active
            transport.onError { error -> onTransportError(active, error) }
            transport.onClose { onTransportClosed(active) }
            val authMethod = info.authMethods.firstOrNull()
            if (authMethod == null) {
                mutableState.value = EngineState.Ready
            } else {
                pendingAuthMethodId = authMethod.id.value
                mutableState.value = EngineState.AuthorizationRequired(
                    authMethodId = authMethod.id.value,
                    label = authMethod.name,
                )
            }
        } catch (cancelled: CancellationException) {
            stdio.terminate()
            throw cancelled
        } catch (error: Exception) {
            stdio.terminate()
            failConnect(
                token,
                (error.message ?: "The agent rejected the connection") + stdio.diagnosticsSuffix(),
            )
        }
    }

    private fun failConnect(token: Any, reason: String) {
        if (connectToken === token) {
            mutableState.value = EngineState.Failed(reason)
        }
    }

    /**
     * The agent closed the connection or its process died. The runtime sessions become
     * unavailable and the engine reports a failure, so the UI cannot keep a dead `Ready` state.
     */
    private fun onTransportClosed(closed: Connection) {
        if (connection !== closed) return
        connection = null
        pendingAuthMethodId = null
        closeAllSessions(SessionState.Unavailable)
        mutableCapabilities.value = EngineCapabilities()
        val failure = transportFailure
        transportFailure = null
        mutableState.value = EngineState.Failed(
            failure?.let { "The agent connection was lost: $it" }
                ?: "The agent connection was lost",
        )
        runCatching { closed.stdio.terminate() }
    }

    private fun onTransportError(closed: Connection, error: Throwable) {
        if (connection !== closed) return
        transportFailure = error.message ?: error::class.simpleName
    }

    private fun onAuthorizationCompleted(result: AuthResult) {
        when (result) {
            AuthResult.Success -> authenticate()
            AuthResult.Cancelled -> disconnect()
            is AuthResult.Failure -> {
                mutableState.value = EngineState.Failed(result.reason)
                stopConnection()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // Authentication failures surface as engine state.
    private fun authenticate() {
        val active = connection ?: return
        val methodId = pendingAuthMethodId ?: return
        mutableState.value = EngineState.Connecting
        engineScope.launch {
            try {
                active.client.authenticate(AuthMethodId(methodId))
                if (connection === active) {
                    mutableState.value = EngineState.Ready
                }
            } catch (error: Exception) {
                mutableState.value = EngineState.Failed(
                    error.message ?: "Authentication failed",
                )
                stopConnection()
            }
        }
    }

    private fun disconnect() {
        connectToken = null
        connectJob?.cancel()
        connectJob = null
        val active = connection
        connection = null
        closeAllSessions(SessionState.Unavailable)
        mutableState.value = EngineState.NotInitialized
        mutableCapabilities.value = EngineCapabilities()
        pendingAuthMethodId = null
        if (active != null) {
            closeConnection(active)
        }
    }

    private fun stopConnection() {
        val active = connection ?: return
        connection = null
        closeConnection(active)
    }

    private fun closeConnection(active: Connection) {
        runCatching { active.protocol.close() }
        active.stdio.terminate()
    }

    private fun readyConnection(): Connection {
        val active = connection
        if (mutableState.value !is EngineState.Ready || active == null) {
            throw AiEngineException.NotReady(mutableState.value)
        }
        return active
    }

    private fun requireCapability(capability: EngineCapability) {
        if (!capabilities.value.supports(capability)) {
            throw AiEngineException.Unsupported(capability)
        }
    }

    private fun parameters(cwd: String): SessionCreationParameters = SessionCreationParameters(
        cwd = cwd,
        mcpServers = listOf(),
    )

    private fun defaultWorkingDirectory(): String =
        activeLaunch?.workingDirectory ?: System.getProperty("user.dir")

    private fun register(session: AcpSession) {
        synchronized(sessionLock) {
            sessionsByKey[session.key] = session
            publishSessionsLocked()
        }
    }

    private fun closeAllSessions(next: SessionState) {
        synchronized(sessionLock) {
            sessionsByKey.values.forEach { it.shutdown(next) }
            sessionsByKey.clear()
            publishSessionsLocked()
        }
    }

    private fun publishSessionsLocked() {
        mutableSessions.value = sessionsByKey.keys.toList()
    }
}

private fun AgentCapabilities.toEngineCapabilities(): EngineCapabilities = EngineCapabilities(
    supported = buildSet {
        if (sessionCapabilities.list != null) add(EngineCapability.SESSION_LIST)
        if (sessionCapabilities.resume != null) add(EngineCapability.SESSION_RESUME)
        if (sessionCapabilities.close != null) add(EngineCapability.SESSION_CLOSE)
    },
)

/** Stderr tail of the agent process, appended to a failure so the user sees the real cause. */
private fun AcpStdio.diagnosticsSuffix(): String {
    val details = diagnostics()
    return if (details.isBlank()) "" else "\n$details"
}
