package io.github.aequicor.heartbeat.features.aiengine.api.session

/** A missing selector means that the engine does not expose this setting. */
public data class SessionSettings(val model: Selector? = null, val reasoning: Selector? = null) {

    public data class Selector(val id: String, val current: String, val choices: List<Choice>)

    public data class Choice(val id: String, val name: String)
}
