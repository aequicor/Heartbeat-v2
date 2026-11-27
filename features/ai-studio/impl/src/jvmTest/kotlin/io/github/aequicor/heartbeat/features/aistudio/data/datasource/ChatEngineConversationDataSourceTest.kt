package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindow
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindowCursor
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionBackgroundTurn
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSettings
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.BackgroundChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatEngineConversationDataSourceTest {

    @Test
    fun reportsFastEmptyCompletionAndKeepsTheNextAnswerInItsOwnTurn() = runTest {
        val conversation = ChatEngineConversationDataSourceImpl(
            session = ImmediateSession(),
            attachments = ChatAttachmentDataSourceImpl(".", StandardTestDispatcher(testScheduler)),
            validateSecurityMode = { SessionSettings() },
            closeSession = {},
        )
        val first = mutableListOf<ChatEngineTurn>()
        conversation.send("Первый запрос", emptyList()) { first.add(it) }

        assertEquals(listOf(ChatEngineTurn.Status.Running, ChatEngineTurn.Status.Completed), first.map { it.status })
        assertEquals("", first.last().text)

        val next = mutableListOf<ChatEngineTurn>()
        conversation.send("Продолжай", emptyList()) { next.add(it) }

        assertEquals(listOf(ChatEngineTurn.Status.Running, ChatEngineTurn.Status.Completed), next.map { it.status })
        assertEquals("Итоговый ответ", next.last().text)
    }

    @Test
    fun preservesBackgroundResultAcrossChunksAndTheNextPrompt() = runTest {
        val session = ImmediateSession()
        val conversation = ChatEngineConversationDataSourceImpl(
            session = session,
            attachments = ChatAttachmentDataSourceImpl(".", StandardTestDispatcher(testScheduler)),
            validateSecurityMode = { SessionSettings() },
            closeSession = {},
        )
        val updates = mutableListOf<BackgroundChatTurn>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { conversation.observeBackground { updates.add(it) } }
        val turn = SessionBackgroundTurn("one", "2026-11-27T12:34:00Z")
        val item = SessionTimelineItem.AgentMessage("answer", listOf(SessionContent.Text("Background")))
        val first = turn.copy(items = listOf(item))
        session.state.value = session.state.value.copy(backgroundTurns = listOf(first))
        runCurrent()
        val complete = first.copy(items = listOf(item.copy(content = item.content + SessionContent.Text(" result"))))
        session.state.value = session.state.value.copy(backgroundTurns = listOf(complete))
        runCurrent()
        conversation.send("Продолжай", emptyList()) {}
        runCurrent()

        assertEquals(listOf("Background", "Background result"), updates.map { it.reply?.text })
        assertEquals(listOf("background-one"), updates.map { it.reply?.id }.distinct())
    }

    private class ImmediateSession : Session {

        override val key = SessionKey(object : AiEngineKey {
            override val id = "test-acp"
            override val name = "Test ACP agent"
            override val description = name
            override val connection = AiEngineConnection.ACP
        }, "session")

        override val state = MutableStateFlow(
            SessionState(
                SessionStatus.Idle,
                MessageWindow(emptyList(), MessageWindowCursor(), HistorySyncStatus.LOCAL_ONLY),
            ),
        )

        override fun dispatch(event: SessionEvent) {
            val prompt = assertIs<SessionEvent.SendPrompt>(event)
            val current = state.value
            val turnId = checkNotNull(prompt.turnId)
            val user = SessionTimelineItem.UserMessage(turnId, listOf(SessionContent.Text(prompt.text)))
            val answer = if (current.turn == null) emptyList() else listOf(
                SessionTimelineItem.AgentMessage("answer", listOf(SessionContent.Text("Итоговый ответ"))),
            )
            val unsolicited = if (current.turn == null && current.backgroundTurns.isEmpty()) {
                listOf(SessionBackgroundTurn(
                    "late", "2026-11-27T12:30:00Z",
                    listOf(SessionTimelineItem.AgentMessage("late-answer", listOf(SessionContent.Text("Late output")))),
                ))
            } else emptyList()
            state.value = current.copy(
                messages = current.messages.copy(items = current.messages.items + user + answer + unsolicited.flatMap { it.items }),
                turn = SessionTurn(turnId, SessionTurn.Status.Completed, accepted = true),
                backgroundTurns = current.backgroundTurns + unsolicited,
            )
        }
    }
}
