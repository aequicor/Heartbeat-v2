package io.github.aequicor.heartbeat.features.acp.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AcpMethod.AgentMethods.V1
import com.agentclientprotocol.model.AcpMethod.ClientMethods.V1 as ClientMethods
import com.agentclientprotocol.model.CloseSessionRequest
import com.agentclientprotocol.model.CreateElicitationResponse
import com.agentclientprotocol.model.ElicitationAction
import com.agentclientprotocol.model.ElicitationMode
import com.agentclientprotocol.model.ElicitationScope
import com.agentclientprotocol.model.InitializeResponse
import com.agentclientprotocol.model.LoadSessionRequest
import com.agentclientprotocol.model.NewSessionRequest
import com.agentclientprotocol.model.PROTOCOL_VERSION_V1
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.ResumeSessionRequest
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.invoke
import com.agentclientprotocol.protocol.jsonRpcInvalidParams
import com.agentclientprotocol.protocol.setRequestHandler
import com.agentclientprotocol.rpc.ACPJson
import com.agentclientprotocol.transport.StdioTransport
import com.agentclientprotocol.transport.Transport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
                    val line = reader.readLine() ?: break
                    emit(withModeOptionNames(withSdkAuthCapabilities(line)))
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
    private val childOwners = mutableMapOf<SessionId, AcpSession>()
    private var creating = false
    private val earlyUpdates = mutableListOf<Pair<SessionId, SessionUpdate>>()

    init {
        protocol.setNotificationHandlerRaw(ClientMethods.SessionUpdate) { notification ->
            val params = notification.params as? JsonObject ?: return@setNotificationHandlerRaw
            val id = params.string("sessionId")?.let(::SessionId) ?: return@setNotificationHandlerRaw
            val payload = params["update"] as? JsonObject ?: return@setNotificationHandlerRaw
            val update = ACPJson.decodeFromJsonElement(SessionUpdate.serializer(), payload.withSdkToolContent())
            synchronized(routingLock) {
                if (!route(id, update) && creating) earlyUpdates.add(id to update)
            }
        }
        protocol.setRequestHandler(ClientMethods.SessionRequestPermission) { request ->
            val (session, childId) = requestOwner(request.sessionId)
            session?.requestPermission(request, childId) ?: RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
        }
        protocol.setRequestHandler(ClientMethods.ElicitationCreate) { request ->
            val form = request.mode as? ElicitationMode.Form
                ?: jsonRpcInvalidParams("Only form elicitation is supported")
            val sessionId = when (val scope = request.scope) {
                is ElicitationScope.Session -> scope.sessionId
                is ElicitationScope.Request -> protocol.getOutgoingRequestSessionId(scope.requestId)
            }
            val (session, childId) = requestOwner(sessionId)
            session?.requestQuestionnaire(request.message, form.requestedSchema, childId)
                ?: CreateElicitationResponse(ElicitationAction.Cancel)
        }
    }

    suspend fun initialize() {
        protocol.start()
        val params = buildJsonObject {
            put("protocolVersion", ACPJson.encodeToJsonElement(PROTOCOL_VERSION_V1))
            putJsonObject("clientCapabilities") {
                putJsonObject("elicitation") { putJsonObject("form") {} }
                putJsonObject("subagents") {}
            }
        }
        agent = ACPJson.decodeFromJsonElement(
            InitializeResponse.serializer(), protocol.sendRequestRaw(V1.Initialize.methodName, params),
        )
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
                    session.configuration.initialize(response)
                    sessions[response.sessionId] = session
                    earlyUpdates.forEach { (id, update) -> route(id, update) }
                }
            }
        } finally {
            synchronized(routingLock) {
                creating = false
                earlyUpdates.clear()
            }
        }
    }

    suspend fun reopen(session: AcpSession, directory: String) {
        val replay = agent.agentCapabilities.loadSession
        check(replay || agent.agentCapabilities.sessionCapabilities.resume != null) {
            "Agent does not support reopening sessions"
        }
        val id = SessionId(session.key.id)
        synchronized(routingLock) { sessions[id] = session }
        val response = if (replay) {
            V1.SessionLoad(protocol, LoadSessionRequest(id, directory, listOf()))
        } else {
            V1.SessionResume(protocol, ResumeSessionRequest(id, directory))
        }
        session.configuration.initialize(response)
    }

    fun forget(session: AcpSession) {
        synchronized(routingLock) {
            sessions.remove(SessionId(session.key.id), session)
            childOwners.values.removeAll { it === session }
        }
    }

    /** Both roots and their descendants use the same ordered notification handler. */
    private fun route(id: SessionId, update: SessionUpdate): Boolean {
        val root = sessions[id] ?: childOwners[id] ?: return false
        if (update is SessionUpdate.UnknownSessionUpdate && update.sessionUpdateType == "subagent_update") {
            val childId = update.rawJson.string("sessionId")?.let(::SessionId) ?: return true
            if (childId != id && childId !in sessions) {
                val owner = childOwners.getOrPut(childId) { root }
                if (owner === root) root.announceSubagent(id, childId, update.rawJson)
            }
        } else if (id in sessions) {
            root.update(update)
        } else {
            root.updateSubagent(id, update)
        }
        return true
    }

    private fun requestOwner(id: SessionId?): Pair<AcpSession?, SessionId?> = synchronized(routingLock) {
        val child = childOwners[id]
        if (child == null) sessions[id] to null else child to id
    }

    suspend fun closeSession(id: String) {
        if (agent.agentCapabilities.sessionCapabilities.close != null) {
            V1.SessionClose(protocol, CloseSessionRequest(SessionId(id)))
        }
    }

    override fun close() {
        try {
            process.terminateTree()
        } finally {
            protocol.close()
        }
    }
}

// Represent standard session references in the SDK's existing display-content model.
private fun JsonObject.withSdkToolContent(): JsonObject {
    if (string("sessionUpdate") !in listOf("tool_call", "tool_call_update")) return this
    val content = this["content"] as? JsonArray ?: return this
    val mapped = content.map { element ->
        val item = element as? JsonObject ?: return@map element
        if (item.string("type") != "session") return@map item
        val label = item.string("title") ?: item.string("sessionId").orEmpty()
        buildJsonObject {
            put("type", "content")
            putJsonObject("content") {
                put("type", "text")
                put("text", label)
            }
        }
    }
    return JsonObject(this + ("content" to JsonArray(mapped)))
}

internal fun Process.terminateTree() {
    val descendants = descendants().use { it.toList() }
    try {
        destroyForcibly()
    } finally {
        descendants.forEach { it.destroyForcibly() }
    }
}
