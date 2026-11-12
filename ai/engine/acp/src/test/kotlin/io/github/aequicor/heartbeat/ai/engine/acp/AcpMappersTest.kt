package io.github.aequicor.heartbeat.ai.engine.acp

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PermissionOption as AcpPermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind as AcpOptionKind
import com.agentclientprotocol.model.SessionUpdate as AcpSessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus as AcpToolCallStatus
import io.github.aequicor.heartbeat.ai.engine.api.PermissionOptionKind
import io.github.aequicor.heartbeat.ai.engine.api.SessionUpdate
import io.github.aequicor.heartbeat.ai.engine.api.ToolCallStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AcpMappersTest {

    @Test
    fun agentTextBecomesAgentTextUpdate() {
        val update = AcpSessionUpdate.AgentMessageChunk(ContentBlock.Text("hello"))

        assertEquals(SessionUpdate.AgentText("hello"), update.toEngineUpdate())
    }

    @Test
    fun agentThoughtBecomesThoughtUpdate() {
        val update = AcpSessionUpdate.AgentThoughtChunk(ContentBlock.Text("thinking"))

        assertEquals(SessionUpdate.AgentThought("thinking"), update.toEngineUpdate())
    }

    @Test
    fun toolCallsKeepIdTitleAndStatus() {
        val created = AcpSessionUpdate.ToolCall(
            toolCallId = ToolCallId("tool-1"),
            title = "Run tests",
            status = AcpToolCallStatus.PENDING,
        )
        val updated = AcpSessionUpdate.ToolCallUpdate(
            toolCallId = ToolCallId("tool-1"),
            status = AcpToolCallStatus.COMPLETED,
        )

        assertEquals(
            SessionUpdate.ToolCall("tool-1", "Run tests", ToolCallStatus.PENDING),
            created.toEngineUpdate(),
        )
        assertEquals(
            SessionUpdate.ToolCall("tool-1", "", ToolCallStatus.COMPLETED),
            updated.toEngineUpdate(),
        )
    }

    @Test
    fun planUpdatesAreNotChatUpdates() {
        assertNull(
            AcpSessionUpdate.PlanUpdate(entries = listOf()).toEngineUpdate(),
        )
    }

    @Test
    fun permissionOptionsMapToStableKinds() {
        val options = listOf(
            AcpPermissionOption(PermissionOptionId("allow"), "Allow once", AcpOptionKind.ALLOW_ONCE),
            AcpPermissionOption(PermissionOptionId("always"), "Allow always", AcpOptionKind.ALLOW_ALWAYS),
            AcpPermissionOption(PermissionOptionId("deny"), "Reject", AcpOptionKind.REJECT_ONCE),
        )

        assertEquals(
            listOf(
                PermissionOptionKind.ALLOW_ONCE,
                PermissionOptionKind.ALLOW_ALWAYS,
                PermissionOptionKind.REJECT_ONCE,
            ),
            options.map { it.toEngineOption().kind },
        )
        assertEquals("allow", options[0].toEngineOption().id)
    }
}
