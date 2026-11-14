package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindow
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindowCursor
import io.github.aequicor.heartbeat.features.aiengine.api.session.PendingPermission
import io.github.aequicor.heartbeat.features.aiengine.api.session.PermissionOption
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionPage
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultAiStudioStoreTest {
    @Test
    fun connectsAndOpensSessionsForTheSelectedDirectory() = runTest {
        val agent = TestEngine()
        val store = createStore(agent)
        store.dispatch(AiStudioAction.EditDirectory("relative"))
        store.dispatch(AiStudioAction.Connect)
        runCurrent()
        assertEquals(0, agent.connects)
        assertTrue(store.state.value.error.orEmpty().contains("абсолютный"))

        store.dispatch(AiStudioAction.EditDirectory(Workspace))
        agent.failConnection = true
        store.dispatch(AiStudioAction.Connect)
        runCurrent()
        assertIs<AiEngineState.Failed>(store.state.value.connection)

        agent.failConnection = false
        store.dispatch(AiStudioAction.Connect)
        runCurrent()
        assertTrue(store.state.value.connected)
        assertEquals(SessionListConfig.ByProject(Workspace, workingDirectory = Workspace), agent.listing)
        assertEquals(listOf(agent.saved), store.state.value.sessions)
        assertTrue(store.screenState.value.hasMoreSessions)

        store.dispatch(AiStudioAction.LoadMoreSessions)
        runCurrent()
        assertEquals("page-2", agent.listing?.cursor)
        assertEquals(listOf(agent.saved, agent.other), store.state.value.sessions)
        assertFalse(store.screenState.value.hasMoreSessions)

        store.dispatch(AiStudioAction.OpenChat(agent.saved))
        runCurrent()
        assertEquals(agent.saved, store.state.value.selected)
        assertEquals(Workspace, agent.openedWith?.workingDirectory)
        store.dispatch(AiStudioAction.NewChat)
        runCurrent()
        assertEquals(agent.created.key, store.state.value.selected)
    }

    @Test
    fun streamsPermissionsAndKeepsTheDraftWhenATurnFails() = runTest {
        val agent = TestEngine()
        val store = createStore(agent)
        store.dispatch(AiStudioAction.EditDirectory(Workspace))
        store.dispatch(AiStudioAction.Connect)
        runCurrent()
        store.dispatch(AiStudioAction.NewChat)
        runCurrent()
        val session = agent.created

        store.dispatch(AiStudioAction.EditDraft("Проверь README.md"))
        store.dispatch(AiStudioAction.Send)
        runCurrent()
        assertTrue(store.state.value.turnActive)
        assertEquals(SessionEvent.SendPrompt("Проверь README.md"), session.events.single())

        val option = PermissionOption("allow", "Да", "allow")
        val permission = PendingPermission("request", "tool", "README.md", listOf(option))
        session.state.value = session.state.value.copy(status = SessionStatus.AwaitingPermission(listOf(permission)))
        runCurrent()
        assertIs<SessionStatus.AwaitingPermission>(store.state.value.session?.status)
        assertEquals(SessionStatus.AwaitingPermission(listOf(permission)), store.screenState.value.sessionStatus)
        store.dispatch(AiStudioAction.AnswerPermission(permission.requestId, "allow"))
        store.dispatch(AiStudioAction.CancelTurn)
        runCurrent()
        val actions = listOf(SessionEvent.AnswerPermission("request", "allow"), SessionEvent.Cancel)
        assertEquals(actions, session.events.drop(1))

        session.state.value = session.state.value.copy(status = SessionStatus.Failed("Ошибка агента"))
        runCurrent()
        assertEquals("Проверь README.md", store.state.value.draft)
        assertTrue(store.state.value.canSend)

        store.dispatch(AiStudioAction.Send)
        runCurrent()
        val response = SessionTimelineItem.AgentMessage("reply", listOf(SessionContent.Text("Готово")))
        session.state.value = SessionState(
            SessionStatus.Idle,
            session.state.value.messages.copy(items = session.state.value.messages.items + response),
        )
        runCurrent()
        assertEquals("", store.state.value.draft)
        assertEquals(response, store.state.value.session?.messages?.items?.last())
        assertEquals(response, store.screenState.value.timeline.last())
        assertFalse(store.state.value.busy)
    }

    @Test
    fun reportsAnUnavailableEngineWithoutCreatingARuntime() = runTest {
        val store = createStore(null)
        store.dispatch(AiStudioAction.EditDirectory(Workspace))
        store.dispatch(AiStudioAction.Connect)
        runCurrent()
        assertFalse(store.state.value.engineAvailable)
        assertFalse(store.state.value.busy)
        assertEquals(AiEngineState.NotInitialized, store.state.value.connection)
    }

    private fun TestScope.createStore(engine: TestEngine?): DefaultAiStudioStore {
        val facade = object : AiEngineFacade {
            override val availableEngines = MutableStateFlow(listOfNotNull(engine?.key))
            override val engines = MutableStateFlow<Map<AiEngineKey, AiEngineState>>(mapOf())
            override fun getEngine(key: AiEngineKey): AiEngine = checkNotNull(engine)
            override suspend fun dispose() = Unit
        }
        return DefaultAiStudioStore(facade, backgroundScope)
    }

    private class TestEngine : AiEngine {
        override val key = object : AiEngineKey {
            override val id = "dsh"
            override val name = "DeepSeek Harness"
            override val description = "Test runtime"
            override val connection = AiEngineConnection.ACP
        }
        override val state = MutableStateFlow<AiEngineState>(AiEngineState.NotInitialized)
        override val sessions = MutableStateFlow<Map<SessionKey, SessionStatus>>(mapOf())
        val saved = SessionKey(key, "saved")
        val other = SessionKey(key, "other")
        val created = TestSession(SessionKey(key, "new"))
        var failConnection = false
        var connects = 0
        var listing: SessionListConfig.ByProject? = null
        var openedWith: SessionConfig? = null

        override fun dispatch(event: AiEngineEvent) {
            when (event) {
                AiEngineEvent.Connect -> {
                    connects++
                    state.value = if (failConnection) {
                        AiEngineState.Failed("Не удалось запустить DSH")
                    } else {
                        AiEngineState.Ready
                    }
                }
                AiEngineEvent.Disconnect -> state.value = AiEngineState.NotInitialized
                is AiEngineEvent.AuthorizationCompleted -> Unit
            }
        }

        override suspend fun getListSessions(config: SessionListConfig): SessionPage {
            listing = config as SessionListConfig.ByProject
            return if (config.cursor == null) SessionPage(listOf(saved), "page-2") else SessionPage(listOf(other))
        }

        override suspend fun getOrCreateSession(config: SessionConfig, existing: SessionKey?): Session {
            openedWith = config
            return if (existing == null) created else TestSession(existing)
        }

        override suspend fun closeSession(key: SessionKey) = Unit
        override suspend fun dispose() = Unit
    }

    private class TestSession(override val key: SessionKey) : Session {
        override val state = MutableStateFlow(
            SessionState(
                SessionStatus.Idle,
                MessageWindow(listOf(), MessageWindowCursor(), HistorySyncStatus.LOCAL_ONLY),
            ),
        )
        val events = mutableListOf<SessionEvent>()

        override fun dispatch(event: SessionEvent) {
            events += event
            if (event is SessionEvent.SendPrompt) {
                val content = listOf(SessionContent.Text(event.text))
                val entry = SessionTimelineItem.UserMessage("prompt-${events.size}", content)
                state.value = SessionState(
                    SessionStatus.Running,
                    state.value.messages.copy(items = state.value.messages.items + entry),
                )
            }
        }
    }

    private companion object {
        const val Workspace = "/work/project"
    }
}
