package io.github.aequicor.heartbeat.ai.engine.acp

import com.agentclientprotocol.model.PermissionOption as AcpPermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind as AcpOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import io.github.aequicor.heartbeat.ai.engine.api.PermissionPrompt
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PermissionBridgeTest {

    private val options = listOf(
        AcpPermissionOption(PermissionOptionId("allow"), "Allow once", AcpOptionKind.ALLOW_ONCE),
        AcpPermissionOption(PermissionOptionId("deny"), "Reject", AcpOptionKind.REJECT_ONCE),
    )

    @Test
    fun answerCompletesThePendingCallback() = runTest {
        var prompt: PermissionPrompt? = null
        var resolved = 0
        val bridge = PermissionBridge(
            onPrompt = { prompt = it },
            onResolved = { resolved += 1 },
        )

        val response = async { bridge.await("Run command", options) }
        runCurrent()

        val pending = assertNotNull(prompt)
        assertEquals("Run command", pending.title)
        assertEquals(listOf("allow", "deny"), pending.options.map { it.id })
        assertTrue(bridge.answer(pending.requestId, "allow"))

        val outcome = response.await().outcome
        assertIs<RequestPermissionOutcome.Selected>(outcome)
        assertEquals("allow", outcome.optionId.value)
        assertEquals(1, resolved)
    }

    @Test
    fun cancelCompletesThePendingCallback() = runTest {
        val bridge = PermissionBridge(onPrompt = {}, onResolved = {})
        val response = async { bridge.await(null, options) }
        runCurrent()

        bridge.cancel()

        assertIs<RequestPermissionOutcome.Cancelled>(response.await().outcome)
    }

    @Test
    fun staleRequestIdIsIgnored() = runTest {
        var prompt: PermissionPrompt? = null
        val bridge = PermissionBridge(onPrompt = { prompt = it }, onResolved = {})

        val response = async { bridge.await(null, options) }
        runCurrent()

        val requestId = assertNotNull(prompt).requestId
        assertFalse(bridge.answer("unknown", "allow"))
        assertTrue(bridge.answer(requestId, "deny"))

        val outcome = response.await().outcome
        assertIs<RequestPermissionOutcome.Selected>(outcome)
        assertEquals("deny", outcome.optionId.value)
    }

    @Test
    fun resolvingWithoutPendingRequestIsSafe() {
        val bridge = PermissionBridge(onPrompt = {}, onResolved = {})

        assertFalse(bridge.answer("any", "allow"))
        bridge.cancel()
    }
}
