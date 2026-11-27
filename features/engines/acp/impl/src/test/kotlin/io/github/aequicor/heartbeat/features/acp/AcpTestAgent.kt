package io.github.aequicor.heartbeat.features.acp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A subprocess fixture that validates ACP messages independently of the client's SDK types. */
internal object AcpTestAgent {
    private const val Session = "test-session"
    private const val Child = "child-session"
    private const val PermissionId = "permission"

    @JvmStatic
    fun main(args: Array<String>) {
        val profile = args.single()
        System.`in`.bufferedReader(Charsets.UTF_8).use { reader ->
            runAgent(reader.lineSequence(), profile)
        }
    }

    private fun runAgent(lines: Sequence<String>, profile: String) {
        var promptId: JsonElement? = null
        var cancelled = false
        for (line in lines) {
            val message = Json.parseToJsonElement(line).jsonObject
            val id = message["id"]
            val params = message["params"]?.jsonObject
            when (message["method"]?.jsonPrimitive?.content) {
                "initialize" -> initialize(id, params, profile)
                "session/new" -> reply(id, """{"sessionId":"$Session"}""")
                "session/load" -> {
                    check(profile == "default" || profile == "subagents")
                    check(params?.get("sessionId") == JsonPrimitive(Session))
                    chunk("user_message_chunk", "older")
                    chunk("agent_message_chunk", "reply")
                    if (profile == "subagents") {
                        announce("running")
                        update("""{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Recovered child answer"}}""", Child)
                    }
                    reply(id, "{}")
                }
                "session/resume", "session/close" -> {
                    check(profile == "resume")
                    check(params?.get("sessionId") == JsonPrimitive(Session))
                    reply(id, "{}")
                }
                "session/list" -> list(id, checkNotNull(params))
                "session/prompt" -> {
                    check(params?.get("sessionId") == JsonPrimitive(Session))
                    if (params.toString().contains("exit")) return
                    check(params.toString().contains("hello"))
                    promptId = id
                    cancelled = false
                    val toolSession = if (profile == "subagents") Child else Session
                    if (profile == "subagents") {
                        announce("running")
                        update("""{"sessionUpdate":"session_message","messageId":"delegate","senderSessionId":"$Session","recipientSessionId":"$Child","content":[{"type":"text","text":"Child task"}]}""", Child)
                        update("""{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Child started"}}""", Child)
                    }
                    chunk("agent_message_chunk", "Hel")
                    chunk("agent_message_chunk", "lo")
                    val tool = """{"sessionUpdate":"tool_call","toolCallId":"bash","title":"Bash"}"""
                    update(tool, toolSession)
                    emit(
                        """{"jsonrpc":"2.0","id":"$PermissionId","method":"session/request_permission","params":""" +
                            """{"sessionId":"$toolSession","toolCall":{"toolCallId":"bash"},"options":[""" +
                            """{"optionId":"allow","name":"Allow once","kind":"allow_once"}]}}""",
                    )
                }
                "session/cancel" -> cancelled = true
                null -> {
                    check(id == JsonPrimitive(PermissionId))
                    val outcome = message.getValue("result").jsonObject.getValue("outcome").jsonObject
                    val reason = if (cancelled) {
                        check(outcome["outcome"] == JsonPrimitive("cancelled"))
                        "cancelled"
                    } else {
                        check(outcome["optionId"] == JsonPrimitive("allow"))
                        if (profile == "subagents") {
                            update("""{"sessionUpdate":"tool_call_update","toolCallId":"bash","status":"completed"}""", Child)
                            update("""{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Child finished"}}""", Child)
                            announce("idle")
                        } else {
                            update("""{"sessionUpdate":"tool_call_update","toolCallId":"bash","status":"completed"}""")
                        }
                        "end_turn"
                    }
                    reply(promptId, """{"stopReason":"$reason"}""")
                }
                "$/cancel_request" -> Unit
                else -> error("Unexpected ACP message: $message")
            }
        }
    }

    private fun initialize(id: JsonElement?, params: JsonObject?, profile: String) {
        check(params?.get("protocolVersion") == JsonPrimitive(1))
        check(params["clientCapabilities"]?.jsonObject?.get("subagents") == buildJsonObject {})
        val capabilities = when (profile) {
            "minimal" -> "{}"
            "resume" -> """{"sessionCapabilities":{"list":{},"resume":{},"close":{}}}"""
            else -> """{"loadSession":true,"sessionCapabilities":{"list":{}}}"""
        }
        reply(id, """{"protocolVersion":1,"agentCapabilities":$capabilities}""")
    }

    private fun list(id: JsonElement?, params: JsonObject) {
        val second = params["cursor"] == JsonPrimitive("second")
        val session = buildJsonObject {
            put("sessionId", if (second) "other-session" else Session)
            put("cwd", params.getValue("cwd"))
            put("title", "Test session ${ProcessHandle.current().pid()}")
        }
        val result = buildJsonObject {
            put("sessions", JsonArray(listOf(session)))
            if (!second) put("nextCursor", "second")
        }
        reply(id, result.toString())
    }

    private fun chunk(kind: String, text: String) =
        update("""{"sessionUpdate":"$kind","content":{"type":"text","text":"$text"}}""")

    private fun announce(state: String) =
        update("""{"sessionUpdate":"subagent_update","sessionId":"$Child","title":"Child investigator","state":{"state":"$state"}}""")

    private fun update(update: String, sessionId: String = Session) =
        emit("""{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"$sessionId","update":$update}}""")

    private fun reply(id: JsonElement?, result: String) =
        emit("""{"jsonrpc":"2.0","id":${checkNotNull(id)},"result":$result}""")

    private fun emit(line: String) {
        println(line)
        System.out.flush()
    }
}
