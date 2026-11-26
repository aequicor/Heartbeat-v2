package io.github.aequicor.heartbeat.features.acp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The ACP SDK requires a name before it will decode a select option.
internal fun withModeOptionNames(line: String): String {
    if ("\"configOptions\"" !in line) return line
    val message = Json.parseToJsonElement(line) as? JsonObject ?: return line
    val result = message["result"] as? JsonObject
    val params = message["params"] as? JsonObject
    val update = params?.get("update") as? JsonObject
    val payload = result ?: update ?: return line
    val configs = payload["configOptions"] as? JsonArray ?: return line
    val normalized = JsonArray(configs.map { entry ->
        val selector = entry as? JsonObject ?: return@map entry
        if (selector["category"] != JsonPrimitive("mode") || selector["type"] != JsonPrimitive("select")) return@map entry
        withOptionNames(selector)
    })
    if (normalized == configs) return line
    val fixed = JsonObject(payload + ("configOptions" to normalized))
    return if (result != null) JsonObject(message + ("result" to fixed)).toString()
    else JsonObject(message + ("params" to JsonObject(requireNotNull(params) + ("update" to fixed)))).toString()
}

private fun withOptionNames(parent: JsonObject): JsonElement {
    val options = parent["options"] as? JsonArray ?: return parent
    return JsonObject(parent + ("options" to JsonArray(options.map { item ->
        val option = item as? JsonObject ?: return@map item
        val value = option["value"] as? JsonPrimitive
        when {
            "group" in option -> withOptionNames(option)
            value?.isString == true && (option["name"] == null || option["name"] == JsonNull) ->
                JsonObject(option + ("name" to value))
            else -> item
        }
    })))
}
