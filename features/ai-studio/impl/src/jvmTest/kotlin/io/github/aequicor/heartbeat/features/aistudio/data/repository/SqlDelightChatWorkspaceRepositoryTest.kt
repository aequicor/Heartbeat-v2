package io.github.aequicor.heartbeat.features.aistudio.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightChatWorkspaceRepositoryTest {
    @Test
    fun importsTheOldFormatAndReadsOnlySqliteAfterReopen() = runTest {
        val file = Files.createTempFile("ai-studio-workspace", ".db")
        val preferences = LegacyWorkspace(LegacyJson)
        try {
            val url = "jdbc:sqlite:${file.toAbsolutePath()}"
            val imported = JdbcSqliteDriver(url).use { driver ->
                HeartbeatDatabase.Schema.create(driver)
                val workspace = assertNotNull(repository(driver, preferences).load())
                val chat = workspace.chats.first()
                assertEquals(listOf("z", "a"), workspace.chats.map(AuroraChat::id))
                assertEquals(setOf("a"), workspace.archivedChats)
                assertEquals(setOf("project:Alpha"), workspace.collapsedNavigation)
                assertEquals(setOf("Beta"), workspace.expandedProjects)
                assertEquals("z", workspace.selectedId)
                assertEquals("unfinished draft", chat.draft)
                assertEquals("p", chat.anchor.messageId)
                assertEquals(17, chat.anchor.offset)
                assertEquals("notes.md", chat.attachments.single().name)
                assertEquals("p", chat.messages.single().id)
                assertEquals("partial reply", assertNotNull(chat.interrupted).text)
                val recovered = workspace.recover(AuroraChat::recover)
                val history = recovered.chats.first().messages
                assertEquals(ReplyStatus.Stopped, (history.first() as Reply).status)
                assertEquals(Delivery.Failed, (history.last() as User).delivery)
                assertNull(recovered.chats.first().interrupted)
                workspace
            }
            preferences.value = "unreadable legacy data"
            JdbcSqliteDriver(url).use { driver ->
                assertEquals(imported, repository(driver, preferences).load())
            }
            assertEquals(1, preferences.reads)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun rollsBackBothImportAndSaveWhenWorkspaceWriteFails() = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            HeartbeatDatabase.Schema.create(driver)
            val preferences = LegacyWorkspace(LegacyJson)
            val repository = repository(driver, preferences)
            val queries = HeartbeatDatabase(driver).chatQueries
            val rejectWorkspaceWrite = """
                CREATE TRIGGER reject_workspace BEFORE INSERT ON chat_workspace
                BEGIN SELECT RAISE(ABORT, 'workspace write rejected'); END
            """.trimIndent()
            driver.execute(null, rejectWorkspaceWrite, 0)
            assertFails { repository.load() }
            assertNull(queries.selectWorkspace().executeAsOneOrNull())
            assertTrue(queries.selectAll().executeAsList().isEmpty())
            assertEquals(LegacyJson, preferences.value)

            driver.execute(null, "DROP TRIGGER reject_workspace", 0)
            val imported = assertNotNull(repository.load())
            val changed = imported.updateChat("z") { it.copy(draft = "new draft") }.select("a")
            driver.execute(null, rejectWorkspaceWrite, 0)
            assertFails { repository.save(changed) }
            assertEquals(imported, repository.load())

            driver.execute(null, "DROP TRIGGER reject_workspace", 0)
            repository.save(changed)
            assertEquals(changed, repository.load())
        }
    }

    private fun repository(driver: JdbcSqliteDriver, preferences: PreferencesStore) =
        SqlDelightChatWorkspaceRepository(object : DatabaseConnection {
            override val driver = driver
        }, preferences, AuroraChat.serializer())

    private class LegacyWorkspace(var value: String) : PreferencesStore {
        var reads = 0
            private set

        override fun observeString(key: String, default: String): Flow<String> {
            require(key == "workspace_v1")
            reads++
            return flowOf(value)
        }

        override fun observeBoolean(key: String, default: Boolean) = error("Unexpected preference read")
        override fun observeLong(key: String, default: Long) = error("Unexpected preference read")
        override suspend fun putString(key: String, value: String) = error("Unexpected preference write")
        override suspend fun putBoolean(key: String, value: Boolean) = error("Unexpected preference write")
        override suspend fun putLong(key: String, value: Long) = error("Unexpected preference write")
        override suspend fun remove(key: String) = error("Unexpected preference removal")
    }

    private companion object {
        val LegacyJson = """
            {
              "selectedId":"z","serial":12,
              "collapsedNavigation":["project:Alpha"],"expandedProjects":["Beta"],"archivedChats":["a"],
              "chats":[
                {
                  "summary":{"id":"z","title":"First","project":"Alpha"},"draft":"unfinished draft",
                  "anchor":{"messageId":"p","offset":17},
                  "attachments":[{"id":"f","name":"notes.md","kind":"Markdown","content":"# Notes"}],
                  "messages":[{"type":"io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User",
                    "id":"p","time":"14:04","text":"prompt","delivery":"Sending"}],
                  "interrupted":{"id":"r","time":"14:04","text":"partial reply"}
                },
                {"summary":{"id":"a","title":"Second","project":"Beta"}}
              ]
            }
        """.trimIndent()
    }
}
