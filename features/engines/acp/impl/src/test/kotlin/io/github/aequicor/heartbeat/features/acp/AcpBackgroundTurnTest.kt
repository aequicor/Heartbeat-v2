package io.github.aequicor.heartbeat.features.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.ElicitationAction
import com.agentclientprotocol.model.ElicitationPropertySchema
import com.agentclientprotocol.model.ElicitationSchema
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionRequest
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.github.aequicor.heartbeat.features.acp.data.AcpSession
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
internal class AcpBackgroundTurnTest {

    @Test
    fun publishesBackgroundResultsAndUsesExistingRequestAnswers() = runBlocking {
        withTimeout(10.seconds) {
            val input = Channel<String>(Channel.UNLIMITED)
            val output = Channel<String>(Channel.UNLIMITED)
            val protocol = Protocol(this, StdioTransport(this, Dispatchers.IO, input.receiveAsFlow(), output::send))
            val key = object : AiEngineKey {
                override val id = "test-acp"
                override val name = "Test ACP agent"
                override val description = name
                override val connection = AiEngineConnection.ACP
            }
            val session = AcpSession(
                SessionKey(key, "session"), { key }, protocol, this,
                replay = false, dshAgent = false, imagePrompts = false, embeddedContext = false, onStatus = {},
            )
            try {
                protocol.start()
                session.opened()
                session.dispatch(SessionEvent.SendPrompt("hello", "foreground"))
                val promptId = Json.parseToJsonElement(output.receive()).jsonObject.getValue("id")
                input.send("""{"jsonrpc":"2.0","id":$promptId,"result":{"stopReason":"end_turn"}}""")
                session.state.first { it.turn?.status == SessionTurn.Status.Completed }

                val permissionOption = PermissionOption(PermissionOptionId("allow"), "Allow once", PermissionOptionKind.ALLOW_ONCE)
                val tool = SessionUpdate.ToolCallUpdate(
                    ToolCallId("background-bash"), title = "Bash", rawInput = buildJsonObject { put("command", "pwd") },
                )
                val permissionResponse = async {
                    session.requestPermission(RequestPermissionRequest(SessionId("session"), tool, listOf(permissionOption)))
                }
                val pending = session.state.first { it.status is SessionStatus.AwaitingPermission }
                val permission = assertIs<SessionStatus.AwaitingPermission>(pending.status).requests.single()
                assertEquals("pwd", permission.tool?.command)
                assertEquals(SessionTurn.Status.Completed, pending.turn?.status)
                session.dispatch(SessionEvent.AnswerPermission(permission.requestId, "allow"))
                assertEquals(RequestPermissionOutcome.Selected(permissionOption.optionId), permissionResponse.await().outcome)

                val questionnaireResponse = async {
                    session.requestQuestionnaire(
                        "Confirm the plan", ElicitationSchema(properties = mapOf("name" to ElicitationPropertySchema.StringProperty())),
                    )
                }
                val question = session.state.first { it.questionnaires.isNotEmpty() }.questionnaires.single()
                session.dispatch(SessionEvent.AnswerQuestionnaire(
                    question.id, QuestionnaireAnswer.Accept(mapOf("name" to QuestionnaireAnswer.Value.Text("accepted"))),
                ))
                assertIs<ElicitationAction.Accept>(questionnaireResponse.await().action)
                session.update(SessionUpdate.AgentMessageChunk(ContentBlock.Text("Background result")))

                val settled = session.state.value
                val background = settled.backgroundTurns.single()
                val answer = background.items.filterIsInstance<SessionTimelineItem.AgentMessage>().single()
                assertEquals(listOf(SessionContent.Text("Background result")), answer.content)
                assertTrue(settled.questionnaires.isEmpty())
            } finally {
                protocol.close()
            }
        }
        Unit
    }
}
