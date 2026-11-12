package io.github.aequicor.heartbeat.features.aiengineacpadapter.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AcpMethod.AgentMethods.V1
import com.agentclientprotocol.model.AcpMethod.ClientMethods.V1 as ClientMethods
import com.agentclientprotocol.model.CloseSessionRequest
import com.agentclientprotocol.model.InitializeRequest
import com.agentclientprotocol.model.InitializeResponse
import com.agentclientprotocol.model.LoadSessionRequest
import com.agentclientprotocol.model.NewSessionRequest
import com.agentclientprotocol.model.PROTOCOL_VERSION_V1
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.invoke
import com.agentclientprotocol.protocol.setNotificationHandler
import com.agentclientprotocol.protocol.setRequestHandler
import com.agentclientprotocol.transport.StdioTransport
import com.agentclientprotocol.transport.Transport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

@OptIn(UnstableApi::class)
internal class AcpConnection(
    private val process: Process,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val output = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val transport = StdioTransport(
        parentScope = scope,
        ioDispatcher = ioDispatcher,
        input = flow {
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    emit(reader.readLine() ?: break)
                }
            }
        },
        output = { line ->
            output.write(line)
            output.write("\n")
            output.flush()
        },
    )
    val protocol = Protocol(scope, transport)
    lateinit var agent: InitializeResponse
        private set

    private val routingLock = Any()
    private val sessions = mutableMapOf<SessionId, AcpSession>()
    private var creating = false
    private val earlyUpdates = mutableMapOf<SessionId, MutableList<SessionUpdate>>()

    init {
        protocol.setNotificationHandler(ClientMethods.SessionUpdate) { notification ->
            synchronized(routingLock) {
                val session = sessions[notification.sessionId]
                if (session != null) {
                    session.update(notification.update)
                } else if (creating) {
                    earlyUpdates.getOrPut(notification.sessionId, ::mutableListOf).add(notification.update)
                }
            }
        }
        protocol.setRequestHandler(ClientMethods.SessionRequestPermission) { request ->
            val session = synchronized(routingLock) { sessions[request.sessionId] }
            session?.requestPermission(request) ?: RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
        }
    }

    suspend fun initialize() {
        protocol.start()
        agent = V1.Initialize(protocol, InitializeRequest(PROTOCOL_VERSION_V1))
        check(agent.protocolVersion == PROTOCOL_VERSION_V1) {
            "Unsupported ACP protocol version ${agent.protocolVersion}"
        }
    }

    suspend fun awaitClosed() {
        transport.state.first { it == Transport.State.CLOSED }
    }

    suspend fun create(directory: String, createSession: (SessionId) -> AcpSession): AcpSession {
        synchronized(routingLock) { creating = true }
        return try {
            val response = V1.SessionNew(protocol, NewSessionRequest(directory, listOf()))
            synchronized(routingLock) {
                check(response.sessionId !in sessions) { "Agent returned an already open session ID" }
                createSession(response.sessionId).also { session ->
                    sessions[response.sessionId] = session
                    earlyUpdates[response.sessionId]?.forEach(session::update)
                }
            }
        } finally {
            synchronized(routingLock) {
                creating = false
                earlyUpdates.clear()
            }
        }
    }

    suspend fun load(session: AcpSession, directory: String) {
        check(agent.agentCapabilities.loadSession) { "Agent does not support loading sessions" }
        val id = SessionId(session.key.id)
        synchronized(routingLock) { sessions[id] = session }
        V1.SessionLoad(protocol, LoadSessionRequest(id, directory, listOf()))
    }

    fun forget(session: AcpSession) {
        synchronized(routingLock) { sessions.remove(SessionId(session.key.id), session) }
    }

    suspend fun closeSession(id: String) {
        if (agent.agentCapabilities.sessionCapabilities.close != null) {
            V1.SessionClose(protocol, CloseSessionRequest(SessionId(id)))
        }
    }

    override fun close() {
        try {
            process.destroyForcibly()
        } finally {
            protocol.close()
        }
    }
}
