package io.github.aequicor.heartbeat.features.aistudio

internal fun String.isSubagentToolName(): Boolean {
    val name = trim().lowercase()
    return name == "agent" || name == "task" || name == "subagent" ||
        name == "spawn_agent" || name == "spawn agent" || name.startsWith("subagent_")
}

internal fun String.subagentNameFromOutput(): String? {
    val line = lineSequence().firstOrNull { it.startsWith("started subagent ") } ?: return null
    return line.removePrefix("started subagent ").trim().takeIf { it.matches(Regex("[\\p{L}\\p{N}_.-]{1,80}")) }
}
