// The in-process agent owns its dispatchers, and the SDK fixes the suspending ACP signatures.
@file:Suppress("InjectDispatcher", "SuspendFunWithFlowReturnType")
@file:OptIn(UnstableApi::class)

package io.github.aequicor.heartbeat.ai.engine.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionCapabilities
import com.agentclientprotocol.model.SessionCloseCapabilities
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionInfo
import com.agentclientprotocol.model.SessionListCapabilities
import com.agentclientprotocol.model.SessionResumeCapabilities
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineException
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
    private val agent = Agent(protocol, StubSupport(advertiseResume))

    var terminated: Boolean = false
        private set

    fun start() {
        check(agent.protocol === protocol)
        protocol.start()
    }

    fun clientStdio(): AcpStdio = object : AcpStdio {
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

    private class StubSupport(
        private val advertiseResume: Boolean,
    ) : AgentSupport {
        override suspend fun initialize(clientInfo: ClientInfo): AgentInfo = AgentInfo(
            capabilities = AgentCapabilities(
                sessionCapabilities = SessionCapabilities(
                    list = SessionListCapabilities(),
                    resume = if (advertiseResume) SessionResumeCapabilities() else null,
                    close = SessionCloseCapabilities(),
                ),
            ),
        )

        override suspend fun createSession(
            sessionParameters: SessionCreationParameters,
        ): AgentSession = StubSession(SessionId("stub-session"))

        override suspend fun resumeSession(
            sessionId: SessionId,
            sessionParameters: SessionCreationParameters,
        ): AgentSession = StubSession(sessionId)

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
    ) : AgentSession {
        override suspend fun prompt(
            content: List<ContentBlock>,
            _meta: JsonElement?,
        ): Flow<Event> = flow {
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
