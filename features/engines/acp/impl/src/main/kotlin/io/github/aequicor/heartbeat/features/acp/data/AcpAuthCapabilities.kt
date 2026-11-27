package io.github.aequicor.heartbeat.features.acp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// SDK 0.34 expects a boolean for auth.status. Codex ACP 2.12 advertises support with an object.
internal fun withSdkAuthCapabilities(line: String): String {
    if ("\"agentCapabilities\"" !in line) return line
    val message = Json.parseToJsonElement(line) as? JsonObject ?: return line
    val result = message["result"] as? JsonObject ?: return line
    val capabilities = result["agentCapabilities"] as? JsonObject ?: return line
    val auth = capabilities["auth"] as? JsonObject ?: return line
    if (auth["status"] !is JsonObject) return line
    val updatedAuth = JsonObject(auth + ("status" to JsonPrimitive(true)))
    val updated = JsonObject(capabilities + ("auth" to updatedAuth))
    return JsonObject(message + ("result" to JsonObject(result + ("agentCapabilities" to updated)))).toString()
}
