package io.github.aequicor.heartbeat.features.aiengineacpadapter

import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aiengineacpadapter.data.datasource.AcpAgentDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class AcpAiEngineTest {
    private object Key : AiEngineKey {
        override val id = "test-acp"
        override val name = "Test ACP agent"
        override val description = "Agent that exercises ACP over stdio"
        override val connection = AiEngineConnection.ACP
    }

    private val directory = Path.of("").toAbsolutePath()

    @Test
    fun streamsTurnAndWaitsForAnExplicitPermissionAnswer() = withAgent { engine ->
        val session = engine.getOrCreateSession(SessionConfig(directory.toString()))
        session.dispatch(SessionEvent.SendPrompt("hello"))
        val pending = assertIs<SessionStatus.AwaitingPermission>(
            session.state.first { it.status is SessionStatus.AwaitingPermission }.status,
        )
        val request = pending.requests.single()
        val tool = assertIs<SessionTimelineItem.ToolCall>(
            session.state.value.messages.items.first { it.id == request.toolCallEntryId },
        )
        assertEquals("Write file", tool.title)
        engine.sessions.first { it[session.key] == pending }
        session.dispatch(SessionEvent.AnswerPermission(request.requestId, "unknown"))
        assertEquals(pending, session.state.value.status)
        session.dispatch(SessionEvent.AnswerPermission(request.requestId, "allow"))
        val state = session.state.first { it.status == SessionStatus.Idle }
        val messages = state.messages.items
        val user = assertIs<SessionTimelineItem.UserMessage>(messages[0])
        val agent = assertIs<SessionTimelineItem.AgentMessage>(messages[1])
        assertEquals(listOf(SessionContent.Text("hello")), user.content)
        assertEquals(listOf(SessionContent.Text("Hello")), agent.content)
        val completed = assertIs<SessionTimelineItem.ToolCall>(messages[2])
        assertEquals(tool.id, completed.id)
        assertEquals(ToolCallStatus.COMPLETED, completed.status)
    }

    @Test
    fun listsPagesAndReplaysAnExistingSession() = withAgent { engine ->
        val config = SessionListConfig.ByProject("project")
        val first = engine.getListSessions(config)
        assertEquals("second", first.nextCursor)
        val key = first.sessions.single()
        val pid = checkNotNull(key.title).substringAfterLast(' ').toLong()
        val process = ProcessHandle.of(pid).orElseThrow()
        assertEquals(directory.toString(), key.workingDirectory)
        val second = engine.getListSessions(config.copy(cursor = first.nextCursor))
        assertEquals("other-session", second.sessions.single().id)
        assertEquals(null, second.nextCursor)
        assertFailsWith<IllegalArgumentException> {
            engine.getListSessions(SessionListConfig.ByParent(key))
        }

        val firstLoad = async { engine.getOrCreateSession(SessionConfig(), key) }
        val secondLoad = async { engine.getOrCreateSession(SessionConfig(), key) }
        val session = firstLoad.await()
        assertSame(session, secondLoad.await())
        val history = session.state.value.messages
        assertEquals(HistorySyncStatus.AVAILABLE, history.sync)
        val user = assertIs<SessionTimelineItem.UserMessage>(history.items[0])
        val agent = assertIs<SessionTimelineItem.AgentMessage>(history.items[1])
        assertEquals(listOf(SessionContent.Text("older")), user.content)
        assertEquals(listOf(SessionContent.Text("reply")), agent.content)

        engine.closeSession(key)
        assertEquals(SessionStatus.Closed, session.state.value.status)
        assertTrue(engine.sessions.value.isEmpty())
        assertNotSame(session, engine.getOrCreateSession(SessionConfig(), key))
        engine.dispatch(AiEngineEvent.Disconnect)
        engine.state.first { it == AiEngineState.NotInitialized }
        process.onExit().await()
    }

    @Test
    fun cancelsAWaitingTurnAndDetectsAgentExit() = withAgent { engine ->
        val session = engine.getOrCreateSession(SessionConfig(directory.toString()))
        session.dispatch(SessionEvent.SendPrompt("hello"))
        session.state.first { it.status is SessionStatus.AwaitingPermission }
        session.dispatch(SessionEvent.Cancel)
        val finished = session.state.first { it.status == SessionStatus.Idle }
        assertEquals(
            ToolCallStatus.CANCELLED,
            finished.messages.items.filterIsInstance<SessionTimelineItem.ToolCall>().single().status,
        )
        session.dispatch(SessionEvent.SendPrompt("exit"))
        engine.state.first { it is AiEngineState.Failed }
        assertEquals(SessionStatus.Unavailable, session.state.value.status)
        assertTrue(engine.sessions.value.isEmpty())
        engine.dispatch(AiEngineEvent.Connect)
        engine.state.first { it == AiEngineState.Ready }
    }

    @Test
    fun handlesMissingCapabilitiesAndFailedLaunch() = withAgent("minimal") { engine ->
        val config = SessionConfig(directory.toString())
        val session = engine.getOrCreateSession(config)
        engine.closeSession(session.key)
        assertFailsWith<IllegalStateException> { engine.getOrCreateSession(config, session.key) }
        assertFailsWith<IllegalStateException> { engine.getListSessions(SessionListConfig.ByProject("project")) }

        val command = listOf(directory.resolve("missing-acp-test-agent").toString())
        val missing = AcpAgentDataSource(AppCoroutineScope(this)).provider(Key, command) { null }.create()
        try {
            missing.dispatch(AiEngineEvent.Connect)
            assertIs<AiEngineState.Failed>(missing.state.first { it is AiEngineState.Failed })
        } finally {
            missing.dispose()
        }
    }

    private fun withAgent(
        profile: String = "default",
        test: suspend CoroutineScope.(AiEngine) -> Unit,
    ) = runBlocking {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = checkNotNull(System.getProperty("heartbeat.acp.test.classpath"))
        val command = listOf(java, "-cp", classpath, AcpTestAgent::class.java.name, profile)
        val dataSource = AcpAgentDataSource(AppCoroutineScope(this))
        val engine = dataSource.provider(Key, command) { id -> directory.takeIf { id == "project" } }.create()
        try {
            withTimeout(20.seconds) {
                engine.dispatch(AiEngineEvent.Connect)
                val connected = engine.state.first { it == AiEngineState.Ready || it is AiEngineState.Failed }
                assertEquals(AiEngineState.Ready, connected)
                test(engine)
            }
        } finally {
            engine.dispose()
            assertEquals(AiEngineState.NotInitialized, engine.state.value)
            assertTrue(engine.sessions.value.isEmpty())
        }
    }
}
