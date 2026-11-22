package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatConfiguration(val engineId: String, val model: String?, val reasoning: String?) {

    data class Conversation(val configuration: ChatConfiguration, val engineSessionId: String)
}
