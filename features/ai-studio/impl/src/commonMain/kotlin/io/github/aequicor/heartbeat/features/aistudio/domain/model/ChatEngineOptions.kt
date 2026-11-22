package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatEngineOptions(val model: Selector?, val reasoning: Selector?) {

    data class Choice(val id: String, val name: String)

    data class Selector(val id: String, val current: String, val choices: List<Choice>)
}
