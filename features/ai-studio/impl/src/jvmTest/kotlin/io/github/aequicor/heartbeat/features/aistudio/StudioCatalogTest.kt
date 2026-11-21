package io.github.aequicor.heartbeat.features.aistudio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindow
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindowCursor
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionListConfig
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionPage
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aistudio.data.StudioCatalog
import io.github.aequicor.heartbeat.features.aistudio.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.StudioEngineSessions
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class StudioCatalogTest {
    @Test
    fun readsProjectsAndSessionsOfflineAndRoutesWorkToTheOwningEngine() = runTest {
        val file = Files.createTempFile("studio-catalog", ".db")
        val url = "jdbc:sqlite:" + file.toAbsolutePath()
        val preferences = FakePreferencesStore()
        val engines = Engines()
        fun open(scope: CoroutineScope, driver: JdbcSqliteDriver): AiStudioStore {
            val catalog = StudioCatalog(object : DatabaseConnection { override val driver = driver })
            return DefaultAiStudioStore(preferences, scope, catalog, StudioEngineSessions(engines, catalog))
        }
        try {
            var project: String
            var firstChat: String
            val first = JdbcSqliteDriver(url, Properties(), HeartbeatDatabase.Schema)
            try {
                val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
                try {
                    val store = open(scope, first)
                    store.dispatch(AiStudioAction.OpenProject("/work", "Heartbeat"))
                    runCurrent()
                    project = store.screenState.value.navigation.single().project
                    assertTrue(store.screenState.value.chats.isEmpty())
                    assertTrue(engines.requested.isEmpty())

                    store.dispatch(AiStudioAction.NewProjectChat(project, "one"))
                    runCurrent()
                    firstChat = store.screenState.value.chat!!.id
                    store.dispatch(AiStudioAction.NewProjectChat(project, "two"))
                    runCurrent()
                    assertNotEquals(firstChat, store.screenState.value.chat!!.id)
                    assertEquals(listOf("one", "two"), engines.requested)
                    assertEquals(4, store.screenState.value.chats.size)
                } finally {
                    scope.cancel()
                }
            } finally {
                first.close()
            }
            val second = JdbcSqliteDriver(url, Properties(), HeartbeatDatabase.Schema)
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
            try {
                engines.requested.clear()
                val store = open(scope, second)
                store.dispatch(AiStudioAction.SearchChats("Imported"))
                runCurrent()
                assertEquals(2, store.screenState.value.chats.size)
                assertEquals(1, store.screenState.value.navigation.count { it.kind == NavigationKind.Project })
                assertTrue(engines.requested.isEmpty())
                engines.agents.first().interruptList = true
                store.dispatch(AiStudioAction.OpenChat(firstChat))
                runCurrent()
                assertEquals(listOf("one"), engines.requested)
                assertEquals(listOf(null, "42"), engines.agents.first().opened)
                assertEquals(2, store.screenState.value.chats.size)
            } finally {
                scope.cancel()
                second.close()
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun importsPreviousChatsAndDraftsAndKeepsEmptyProjects() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), HeartbeatDatabase.Schema)
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
        try {
            val preferences = FakePreferencesStore()
            preferences.putString("workspace_v1", """{"selectedId":"saved","chats":[{"summary":{"id":"saved","title":"Прежний чат","project":"Heartbeat"},"draft":"черновик"}]}""")
            val catalog = StudioCatalog(object : DatabaseConnection { override val driver = driver })
            val engines = Engines()
            val store = DefaultAiStudioStore(preferences, scope, catalog, StudioEngineSessions(engines, catalog))
            runCurrent()
            assertEquals("черновик", store.draft.value.text)
            assertEquals("Прежний чат", store.screenState.value.chat?.title)
            assertEquals("", preferences.observeString("workspace_v1").first())
            store.dispatch(AiStudioAction.ArchiveChat("saved"))
            runCurrent()
            assertTrue(store.screenState.value.chats.isEmpty())
            assertEquals("Heartbeat", store.screenState.value.navigation.single().title)
            assertTrue(engines.requested.isEmpty())
        } finally {
            scope.cancel()
            driver.close()
        }
    }

    private class Engines : AiEngineFacade {
        val agents = listOf(Agent("one"), Agent("two"))
        val requested = mutableListOf<String>()
        override val availableEngines = MutableStateFlow(agents.map { it.key })
        override val engines = MutableStateFlow(agents.associate { it.key to it.state.value })
        override fun getEngine(key: AiEngineKey): AiEngine {
            requested += key.id
            return agents.single { it.key.id == key.id }
        }
        override suspend fun dispose() = Unit
    }

    private class Agent(id: String) : AiEngine {
        override val key: AiEngineKey = object : AiEngineKey {
            override val id = id
            override val name = id
            override val description = id
            override val connection = AiEngineConnection.ACP
        }
        val opened = mutableListOf<String?>()
        var interruptList = false
        override val state = MutableStateFlow<AiEngineState>(AiEngineState.NotInitialized)
        override val sessions = MutableStateFlow<Map<SessionKey, SessionStatus>>(emptyMap())
        override fun dispatch(event: AiEngineEvent) {
            check(event == AiEngineEvent.Connect)
            state.value = AiEngineState.Ready
        }
        override suspend fun getOrCreateSession(config: SessionConfig, existing: SessionKey?): Session {
            opened += existing?.id
            val owner = key
            return object : Session {
                override val key = SessionKey(owner, existing?.id ?: "42", workingDirectory = config.workingDirectory)
                override val state = MutableStateFlow(SessionState(SessionStatus.Idle,
                    MessageWindow(emptyList(), MessageWindowCursor(), HistorySyncStatus.AVAILABLE)))
                override fun dispatch(event: SessionEvent) = Unit
            }
        }
        override suspend fun getListSessions(config: SessionListConfig): SessionPage {
            check(opened.isNotEmpty())
            require(config is SessionListConfig.ByProject && config.workingDirectory == "/work")
            return if (config.cursor == null) {
                SessionPage(listOf(SessionKey(key, "42", title = "Created")), "next")
            } else {
                check(!interruptList) { "Second page unavailable" }
                SessionPage(listOf(SessionKey(key, "43", title = "Imported")))
            }
        }
        override suspend fun closeSession(key: SessionKey) = Unit
        override suspend fun dispose() = Unit
    }
}
