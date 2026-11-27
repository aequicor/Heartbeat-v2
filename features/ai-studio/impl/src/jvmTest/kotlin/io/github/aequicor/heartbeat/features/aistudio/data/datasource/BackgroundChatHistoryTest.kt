package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatConversationDaoImpl
import io.github.aequicor.heartbeat.features.aistudio.data.dao.UntrackedChatDraftOwnership
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.aistudio.db.StudioSessionQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class BackgroundChatHistoryTest {

    @Test
    fun updatesTheSameHistoryEntryWhenMoreBackgroundOutputArrives() = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            HeartbeatDatabase.Schema.create(driver)
            StudioSessionQueries(driver).insertSession(
                id = "session", project_id = "00000000-0000-0000-0000-000000000000",
                title = "Background history", sent_seconds = 0, sent_nanos = 0, parent_id = null,
            )
            val connection = object : DatabaseConnection { override val driver = driver }
            val history = ChatConversationDaoImpl(connection, StandardTestDispatcher(testScheduler), Mutex(), UntrackedChatDraftOwnership)
            val reply = ChatHistoryEntry.Reply(
                id = "background-turn", time = "12:00", text = "Background", promptId = null,
                interim = null, status = ChatHistoryEntry.ReplyStatus.Completed, steps = emptyList(), result = null,
            )
            history.save("session", listOf(reply.toTurnRecord()))
            val queries = ChatQueries(driver)
            val chatId = queries.chatForSession("session").executeAsOne()
            val first = queries.selectMessage(chatId, reply.id).executeAsOne()

            history.save("session", listOf(reply.copy(text = "Background result").toTurnRecord()))
            val selected = queries.selectMessage(chatId, reply.id).executeAsOne()

            assertEquals(first.position, selected.position)
            assertEquals(first.message_id, selected.message_id)
            assertNotEquals(first.revision_id, selected.revision_id)
            assertEquals("Background result", queries.selectParts(selected.revision_id).executeAsOne().body)
        }
    }
}
