package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyCodeLine
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent

public data class WorkCodeDetails(val heading: String, val lines: List<ReplyCodeLine>)

public fun WorkEvent.readCodeDetails(): WorkCodeDetails {
    val path = detailLabel
    val extension = path.fileExtension()
    val highlighter = ReplyCodeHighlighter(if (extension == "mjs") "js" else extension)
    var first: Int? = null
    var last: Int? = null
    val lines = buildList {
        if (path != null) add(ReplyCodeLine(path, listOf()))
        details.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            val number = line.take(separator.coerceAtLeast(0)).toIntOrNull()
            if (number == null || number <= 0) {
                add(ReplyCodeLine(line, listOf()))
            } else {
                if (first == null) first = number
                last = number
                val prefixLength = separator + 1
                val highlights = highlighter.line(line.substring(prefixLength)).highlights.map {
                    it.copy(start = it.start + prefixLength, end = it.end + prefixLength)
                }
                add(ReplyCodeLine(line, highlights))
            }
        }
    }
    val range = when {
        first == null -> null
        first == last -> "строка $first"
        else -> "строки $first–$last"
    }
    return WorkCodeDetails(listOfNotNull(extension.languageLabel(), range).joinToString(" · "), lines)
}

public fun WorkEvent.diffCodeDetails(): WorkCodeDetails {
    val newFile = details.lineSequence().firstOrNull { it.startsWith("--- ") } == "--- /dev/null"
    val label = if (newFile) "новый файл" else "изменения"
    val heading = detailLabel.fileExtension().languageLabel() + " · " + label
    val highlighter = ReplyCodeHighlighter("diff")
    val lines = details.lineSequence().mapNotNull { line ->
        when {
            line.startsWith("--- ") -> null
            line.startsWith("+++ ") -> ReplyCodeLine(line.removePrefix("+++ "), listOf())
            line.startsWith("| ") -> ReplyCodeLine(line.removePrefix("| "), listOf())
            else -> highlighter.line(line)
        }
    }.toList()
    return WorkCodeDetails(heading, lines)
}

public fun WorkEvent.commandCodeDetails(): WorkCodeDetails {
    val commandLine = listOfNotNull(detailLabel?.let { "$it>" }, command).joinToString(" ")
    val output = details.ifBlank {
        if (status == StepStatus.Current) "Ожидаю вывод…" else "Вывод не получен"
    }
    val lines = sequenceOf(commandLine, output).flatMap { it.lineSequence() }.map { ReplyCodeLine(it, listOf()) }
    return WorkCodeDetails("Вывод команды", lines.toList())
}

private fun String?.fileExtension() =
    orEmpty().substringAfterLast('/').substringAfterLast('\\').substringAfterLast('.', "").lowercase()

private fun String.languageLabel() = when (this) {
    "kt", "kts" -> "Kotlin"
    "java" -> "Java"
    "js", "jsx", "mjs" -> "JavaScript"
    "ts", "tsx" -> "TypeScript"
    "py" -> "Python"
    "rs" -> "Rust"
    "go" -> "Go"
    "swift" -> "Swift"
    "json" -> "JSON"
    "yaml", "yml" -> "YAML"
    "sh", "bash" -> "Bash"
    "sql" -> "SQL"
    else -> uppercase().ifBlank { "Текст" }
}
