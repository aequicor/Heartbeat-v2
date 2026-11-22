package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

public data class EngineOperationFailure(
    val message: String,
    val logTitle: String,
    val entries: List<Entry>,
) {

    public data class Entry(val time: String, val level: Level, val text: String)

    public enum class Level {

        INFO, ERROR,
    }

    public val logText: String
        get() = entries.joinToString("\n") { "${it.time}  ${it.level}  ${it.text}" }
}
