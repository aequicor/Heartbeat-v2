package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.parseAcpArguments

public data class AcpConnectionForm(
    val preset: Preset = Preset.Manual,
    val name: String = "",
    val command: String = "",
    val argumentText: String = "",
    val separator: Separator = Separator.Space,
    val environment: List<Variable> = listOf(Variable(0)),
    val ignoreEnvironmentNameCase: Boolean = false,
) {

    public enum class Preset(public val label: String, val executable: String, val arguments: List<String>) {

        Codex("ChatGPT / Codex", "codex-acp", listOf()),
        OpenCode("OpenCode", "opencode", listOf("acp")),
        DeepSeek("DeepSeek Harness", "dsh", listOf("--profile", "acp")),
        Qoder("Qoder", "qoder", listOf("--acp")),
        Manual("Вручную", "", listOf()),
    }

    public enum class Separator(public val label: String, val character: Char) {

        Space("Пробел", ' '),
        Comma("Запятая", ','),
    }

    public data class Variable(val rowId: Long, val name: String = "", val value: String = "")

    public fun selectPreset(selected: Preset, newConnection: Boolean): AcpConnectionForm {
        if (selected == Preset.Manual) return copy(preset = selected)
        return copy(
            preset = selected,
            name = if (newConnection) selected.label else name,
            command = selected.executable,
            argumentText = selected.arguments.joinToString(separator.character.toString()),
        )
    }

    public fun variableError(row: Variable): String? = when {
        row.name.isBlank() && row.value.isNotEmpty() -> "Введите имя переменной"
        row.name.isNotBlank() && environment.count { it.name.equals(row.name, ignoreEnvironmentNameCase) } > 1 ->
            "Имя переменной повторяется"
        '=' in row.name || '\u0000' in row.name || '\u0000' in row.value -> "Недопустимый символ"
        else -> null
    }

    public fun arguments(): List<String>? = parseAcpArguments(argumentText, separator.character)

    public val canCheck: Boolean
        get() = command.isNotBlank() && '\u0000' !in command && '\u0000' !in argumentText &&
            arguments() != null && environment.none { variableError(it) != null }

    public val canSave: Boolean
        get() = name.isNotBlank() && canCheck
}
