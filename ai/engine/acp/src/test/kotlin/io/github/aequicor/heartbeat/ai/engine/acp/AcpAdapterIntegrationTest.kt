// The in-process agent owns its dispatchers, and the SDK fixes the suspending ACP signatures.
@file:Suppress("InjectDispatcher", "SuspendFunWithFlowReturnType")
@file:OptIn(UnstableApi::class)

package io.github.aequicor.heartbeat.ai.engine.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.agent.client
import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.AuthenticateResponse
import com.agentclientprotocol.model.CloseSessionResponse
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.SessionCapabilities
import com.agentclientprotocol.model.SessionCloseCapabilities
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionInfo
import com.agentclientprotocol.model.SessionListCapabilities
import com.agentclientprotocol.model.SessionResumeCapabilities
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineException
import io.github.aequicor.heartbeat.ai.engine.api.AuthResult
import io.github.aequicor.heartbeat.ai.engine.api.EngineCapability
import io.github.aequicor.heartbeat.ai.engine.api.EngineConnection
import io.github.aequicor.heartbeat.ai.engine.api.EngineEvent
import io.github.aequicor.heartbeat.ai.engine.api.EngineKey
import io.github.aequicor.heartbeat.ai.engine.api.EngineState
import io.github.aequicor.heartbeat.ai.engine.api.SessionConfig
import io.github.aequicor.heartbeat.ai.engine.api.SessionEvent
import io.github.aequicor.heartbeat.ai.engine.api.SessionKey
import io.github.aequicor.heartbeat.ai.engine.api.SessionState
import io.github.aequicor.heartbeat.ai.engine.api.SessionUpdate as EngineSessionUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * End-to-end test of the ACP adapter against a real ACP agent running in the same JVM.
 *
 * The agent speaks the protocol through the SDK model types over in-memory channels instead of a
 * child process, so the test covers `initialize`, capability discovery, session creation, prompt
 * streaming, resume support and process termination without an external binary.
 */
class AcpAdapterIntegrationTest {

    private val adapterKey = EngineKey(
        id = "acp.stub",
        name = "Stub agent",
        description = "In-process ACP agent",
        connection = EngineConnection.ACP,
    )

    @Test
    fun connectsCreatesSessionAndReceivesTheTurn() = runBlocking {
        val stub = StubAgent()
        val adapter = adapterFor(stub)
        try {
            stub.start()
            adapter.dispatch(EngineEvent.Connect)
            withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }
            assertTrue(adapter.capabilities.value.supports(EngineCapability.SESSION_RESUME))

            val session = adapter.createSession(SessionConfig(WORKSPACE))
            assertEquals(listOf(session.key), adapter.sessions.value)
            assertEquals(session.key, adapter.getSession(session.key)?.key)

            val updates = mutableListOf<EngineSessionUpdate>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                session.updates.take(2).toList(updates)
            }
            session.dispatch(SessionEvent.SendPrompt("hello"))
            withTimeout(TIMEOUT_MILLIS) { session.state.first { it is SessionState.Idle } }
            collector.join()

            assertEquals(
                listOf(EngineSessionUpdate.AgentText("echo: hello")),
                updates.filterIsInstance<EngineSessionUpdate.AgentText>(),
            )
            assertTrue(updates.any { it is EngineSessionUpdate.Completed })
            assertEquals(session.key, adapter.listSessions().sessions.single())
        } finally {
            adapter.dispose()
        }
        assertTrue(stub.terminated)
    }

    @Test
    fun reopeningWithoutResumeCapabilityFails() = runBlocking {
        val stub = StubAgent(advertiseResume = false)
        val adapter = adapterFor(stub)
        try {
            stub.start()
            adapter.dispatch(EngineEvent.Connect)
            withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }
            assertFalse(adapter.capabilities.value.supports(EngineCapability.SESSION_RESUME))

            val failure = assertFailsWith<AiEngineException.Unsupported> {
                adapter.createSession(
                    SessionConfig(WORKSPACE),
                    SessionKey(adapterKey, "stub-session"),
                )
            }
            assertEquals(EngineCapability.SESSION_RESUME, failure.capability)
        } finally {
            adapter.dispose()
        }
    }

    @Test
    fun disconnectStopsTheAgent() = runBlocking {
        val stub = StubAgent()
        val adapter = adapterFor(stub)
        stub.start()
        adapter.dispatch(EngineEvent.Connect)
        withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }

        adapter.dispatch(EngineEvent.Disconnect)

        withTimeout(TIMEOUT_MILLIS) {
            while (!stub.terminated) delay(5)
        }
        assertTrue(adapter.state.value is EngineState.NotInitialized)
    }

    @Test
    fun resumeOpensTheSavedSession() = runBlocking {
        val stub = StubAgent()
        val adapter = adapterFor(stub)
        try {
            stub.start()
            adapter.dispatch(EngineEvent.Connect)
            withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }
            val saved = SessionKey(adapterKey, "stub-session", title = "Stub session")

            val session = adapter.createSession(SessionConfig(WORKSPACE), saved)
            val again = adapter.createSession(SessionConfig(WORKSPACE), saved)

            assertEquals(saved, session.key)
            assertEquals(listOf(session.key), adapter.sessions.value)
            assertSame(session, again)
        } finally {
            adapter.dispose()
        }
    }

    @Test
    fun closeSessionReleasesTheRuntimeSession() = runBlocking {
        val stub = StubAgent()
        val adapter = adapterFor(stub)
        try {
            stub.start()
            adapter.dispatch(EngineEvent.Connect)
            withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }
            val session = adapter.createSession(SessionConfig(WORKSPACE))

            adapter.closeSession(session.key)

            assertEquals(listOf<SessionKey>(), adapter.sessions.value)
            assertTrue(session.state.value is SessionState.Closed)
            assertEquals(null, adapter.getSession(session.key))
        } finally {
            adapter.dispose()
        }
    }

    @Test
    fun permissionRequestRoundTrips() = runBlocking {
        val stub = StubAgent(askPermission = true)
        val adapter = adapterFor(stub)
        try {
            stub.start()
            adapter.dispatch(EngineEvent.Connect)
            withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }
            val session = adapter.createSession(SessionConfig(WORKSPACE))
            val updates = mutableListOf<EngineSessionUpdate>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                session.updates.take(4).toList(updates)
            }

            session.dispatch(SessionEvent.SendPrompt("run tests"))
            val awaiting = withTimeout(TIMEOUT_MILLIS) {
                session.state.first { it is SessionState.AwaitingPermission }
            } as SessionState.AwaitingPermission
            assertEquals("Run tests", awaiting.prompt.title)
            assertEquals("Allow once", awaiting.prompt.options.single().name)

            session.dispatch(
                SessionEvent.AnswerPermission(
                    requestId = awaiting.prompt.requestId,
                    optionId = awaiting.prompt.options.single().id,
                ),
            )
            withTimeout(TIMEOUT_MILLIS) { session.state.first { it is SessionState.Idle } }
            collector.join()

            assertTrue(
                updates.filterIsInstance<EngineSessionUpdate.AgentText>()
                    .any { it.text == "allowed: true" },
            )
            assertTrue(updates.any { it is EngineSessionUpdate.Completed })
        } finally {
            adapter.dispose()
        }
    }

    @Test
    fun connectIsIdempotent() = runBlocking {
        val stub = StubAgent()
        val adapter = adapterFor(stub)
        try {
            stub.start()
            adapter.dispatch(EngineEvent.Connect)
            withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }

            adapter.dispatch(EngineEvent.Connect)
            delay(100)

            assertEquals(1, stub.stdioStarts)
        } finally {
            adapter.dispose()
        }
    }

    @Test
    fun authorizationCompletesBeforeReady() = runBlocking {
        val stub = StubAgent(requireAuth = true)
        val adapter = adapterFor(stub)
        try {
            stub.start()
            adapter.dispatch(EngineEvent.Connect)
            withTimeout(TIMEOUT_MILLIS) {
                adapter.state.first { it is EngineState.AuthorizationRequired }
            }
            assertFalse(stub.authenticated)

            adapter.dispatch(EngineEvent.AuthorizationCompleted(AuthResult.Success))

            withTimeout(TIMEOUT_MILLIS) { adapter.state.first { it is EngineState.Ready } }
            assertTrue(stub.authenticated)
        } finally {
            adapter.dispose()
        }
    }

    private fun adapterFor(stub: StubAgent): AcpAdapter = AcpAdapter(
        key = adapterKey,
        resolveLaunch = { AcpLaunchSpec(executable = "stub") },
        processStarter = { _, _ -> stub.clientStdio() },
    )

    private companion object {
        private const val TIMEOUT_MILLIS = 10_000L
        private const val WORKSPACE = "/workspace"
    }
}

/** ACP agent that mirrors prompts and advertises the capabilities the adapter probes. */
private class StubAgent(
    private val advertiseResume: Boolean = true,
    private val askPermission: Boolean = false,
    private val requireAuth: Boolean = false,
) {
    private val clientToAgent = Channel<String>(Channel.UNLIMITED)
    private val agentToClient = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val protocol = Protocol(
        scope,
        StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = clientToAgent.receiveAsFlow(),
            output = { line -> agentToClient.send(line) },
            name = "stub-agent",
        ),
    )
    private val support = StubSupport(advertiseResume, askPermission, requireAuth)
    private val agent = Agent(protocol, support)

    var terminated: Boolean = false
        private set

    var stdioStarts: Int = 0
        private set

    val authenticated: Boolean
        get() = support.authenticated

    fun start() {
        check(agent.protocol === protocol)
        protocol.start()
    }

    fun clientStdio(): AcpStdio {
        stdioStarts += 1
        return object : AcpStdio {
            override val input: Flow<String> = agentToClient.receiveAsFlow()

            override suspend fun write(line: String) {
                clientToAgent.send(line)
            }

            override fun diagnostics(): String = ""

            override fun terminate() {
                terminated = true
                clientToAgent.close()
                agentToClient.close()
                scope.cancel()
            }
        }
    }

    private class StubSupport(
        private val advertiseResume: Boolean,
        private val askPermission: Boolean,
        private val requireAuth: Boolean,
    ) : AgentSupport {

        var authenticated: Boolean = false
            private set

        override suspend fun initialize(clientInfo: ClientInfo): AgentInfo = AgentInfo(
            capabilities = AgentCapabilities(
                sessionCapabilities = SessionCapabilities(
                    list = SessionListCapabilities(),
                    resume = if (advertiseResume) SessionResumeCapabilities() else null,
                    close = SessionCloseCapabilities(),
                ),
            ),
            authMethods = if (requireAuth) {
                listOf(AuthMethod.AgentAuth(AuthMethodId("login"), "Sign in"))
            } else {
                listOf()
            },
        )

        override suspend fun authenticate(
            methodId: AuthMethodId,
            _meta: JsonElement?,
        ): AuthenticateResponse {
            authenticated = true
            return AuthenticateResponse()
        }

        override suspend fun createSession(
            sessionParameters: SessionCreationParameters,
        ): AgentSession = StubSession(SessionId("stub-session"), askPermission)

        override suspend fun resumeSession(
            sessionId: SessionId,
            sessionParameters: SessionCreationParameters,
        ): AgentSession = StubSession(sessionId, askPermission)

        override suspend fun listSessions(
            cwd: String?,
            additionalDirectories: List<String>?,
            _meta: JsonElement?,
        ): Sequence<SessionInfo> = sequenceOf(
            SessionInfo(
                sessionId = SessionId("stub-session"),
                cwd = WORKSPACE,
                title = "Stub session",
            ),
        )

        private companion object {
            private const val WORKSPACE = "/workspace"
        }
    }

    private class StubSession(
        override val sessionId: SessionId,
        private val askPermission: Boolean,
    ) : AgentSession {

        override suspend fun close(_meta: JsonElement?): CloseSessionResponse = CloseSessionResponse()

        override suspend fun prompt(
            content: List<ContentBlock>,
            _meta: JsonElement?,
        ): Flow<Event> = flow {
            if (askPermission) {
                emit(
                    Event.SessionUpdateEvent(
                        SessionUpdate.ToolCallUpdate(
                            toolCallId = ToolCallId("tool-1"),
                            title = "Run tests",
                            status = com.agentclientprotocol.model.ToolCallStatus.IN_PROGRESS,
                        ),
                    ),
                )
                val response = currentCoroutineContext().client.requestPermissions(
                    toolCall = SessionUpdate.ToolCallUpdate(
                        toolCallId = ToolCallId("tool-1"),
                        title = "Run tests",
                    ),
                    permissions = listOf(
                        PermissionOption(
                            optionId = PermissionOptionId("allow"),
                            name = "Allow once",
                            kind = PermissionOptionKind.ALLOW_ONCE,
                        ),
                    ),
                )
                val allowed = response.outcome is RequestPermissionOutcome.Selected
                emit(
                    Event.SessionUpdateEvent(
                        SessionUpdate.AgentMessageChunk(ContentBlock.Text("allowed: $allowed")),
                    ),
                )
            }
            val text = content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentMessageChunk(ContentBlock.Text("echo: $text")),
                ),
            )
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
        }
    }
}
