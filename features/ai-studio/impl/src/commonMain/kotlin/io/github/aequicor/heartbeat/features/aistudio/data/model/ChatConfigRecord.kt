package io.github.aequicor.heartbeat.features.aistudio.data.model

import kotlinx.serialization.Serializable

@Serializable
internal data class ChatConfigRecord(val engineId: String, val model: String?, val reasoning: String?) {

    @Serializable
    data class Conversation(val configuration: ChatConfigRecord, val engineSessionId: String)
}
