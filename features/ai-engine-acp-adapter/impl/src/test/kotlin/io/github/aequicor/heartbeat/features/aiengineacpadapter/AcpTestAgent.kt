package io.github.aequicor.heartbeat.features.aiengineacpadapter

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
    private const val PermissionId = "permission"

    @JvmStatic
    fun main(args: Array<String>) {
        val minimal = args.single() == "minimal"
        System.`in`.bufferedReader(Charsets.UTF_8).use { reader ->
            runAgent(reader.lineSequence(), minimal)
        }
    }

    private fun runAgent(lines: Sequence<String>, minimal: Boolean) {
        var promptId: JsonElement? = null
        var cancelled = false
        for (line in lines) {
            val message = Json.parseToJsonElement(line).jsonObject
            val id = message["id"]
            val params = message["params"]?.jsonObject
            when (message["method"]?.jsonPrimitive?.content) {
                "initialize" -> {
                    check(params?.get("protocolVersion") == JsonPrimitive(1))
                    val supported = """{"loadSession":true,"sessionCapabilities":{"list":{}}}"""
                    val capabilities = if (minimal) "{}" else supported
                    reply(id, """{"protocolVersion":1,"agentCapabilities":$capabilities}""")
                }
                "session/new" -> reply(id, """{"sessionId":"$Session"}""")
                "session/load" -> {
                    check(params?.get("sessionId") == JsonPrimitive(Session))
                    chunk("user_message_chunk", "older")
                    chunk("agent_message_chunk", "reply")
                    reply(id, "{}")
                }
                "session/list" -> list(id, checkNotNull(params))
                "session/prompt" -> {
                    check(params?.get("sessionId") == JsonPrimitive(Session))
                    if (params.toString().contains("exit")) return
                    check(params.toString().contains("hello"))
                    promptId = id
                    cancelled = false
                    chunk("agent_message_chunk", "Hel")
                    chunk("agent_message_chunk", "lo")
                    val tool = """{"sessionUpdate":"tool_call","toolCallId":"write","title":"Write file"}"""
                    update(tool)
                    emit(
                        """{"jsonrpc":"2.0","id":"$PermissionId","method":"session/request_permission","params":""" +
                            """{"sessionId":"$Session","toolCall":{"toolCallId":"write"},"options":[""" +
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
                        update("""{"sessionUpdate":"tool_call_update","toolCallId":"write","status":"completed"}""")
                        "end_turn"
                    }
                    reply(promptId, """{"stopReason":"$reason"}""")
                }
                "$/cancel_request" -> Unit
                else -> error("Unexpected ACP message: $message")
            }
        }
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

    private fun update(update: String) =
        emit("""{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"$Session","update":$update}}""")

    private fun reply(id: JsonElement?, result: String) =
        emit("""{"jsonrpc":"2.0","id":${checkNotNull(id)},"result":$result}""")

    private fun emit(line: String) {
        println(line)
        System.out.flush()
    }
}
