package io.github.aequicor.heartbeat.features.aienginefacade.domain.model

internal data class AcpConnectionSettings(
    val id: String,
    val name: String,
    val preset: String,
    val command: String,
    val argumentText: String,
    val separator: Char,
    val environment: Map<String, String>,
) {

    fun launchArguments(): List<String>? {
        val validCommand = command.isNotBlank() && '\u0000' !in command
        val validEnvironment = environment.all { (key, value) ->
            val validName = key.isNotBlank() && '=' !in key && '\u0000' !in key
            validName && '\u0000' !in value
        }
        return if (validCommand && validEnvironment && '\u0000' !in argumentText) {
            parseAcpArguments(argumentText, separator)
        } else null
    }
}

/** Returns null for an unfinished quoted argument. Backslashes always represent themselves. */
internal fun parseAcpArguments(text: String, separator: Char): List<String>? {
    if (separator != ' ' && separator != ',') return null
    val result = mutableListOf<String>()
    val token = StringBuilder()
    var quoted = false
    var started = false
    var index = 0
    while (index < text.length) {
        val ch = text[index++]
        when {
            ch == '"' -> {
                started = true
                if (quoted && text.getOrNull(index) == '"') {
                    token.append('"')
                    index++
                } else {
                    quoted = !quoted
                }
            }
            !quoted && ch == separator -> {
                if (started) result.add(token.toString())
                started = false
                token.clear()
            }
            else -> {
                started = true
                token.append(ch)
            }
        }
    }
    if (started) result.add(token.toString())
    return result.takeUnless { quoted }
}
