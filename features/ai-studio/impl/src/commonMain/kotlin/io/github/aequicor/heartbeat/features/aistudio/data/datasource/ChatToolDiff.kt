package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolOutput

internal class ChatToolDiff(val path: String, val body: String, val summary: String)

internal fun List<ToolOutput>.toChatToolDiff(): ChatToolDiff? {
    val patches = filterIsInstance<ToolOutput.Diff>()
    val first = patches.firstOrNull() ?: return null
    var added = 0
    var removed = 0
    var exactCounts = true
    val body = buildString {
        for (result in this@toChatToolDiff) {
            val patch = when (result) {
                is ToolOutput.Diff -> result
                is ToolOutput.Content, is ToolOutput.Command, is ToolOutput.Terminal -> {
                    appendReceipt(result.display())
                    continue
                }
            }
            appendLine("--- " + (patch.path.takeIf { patch.oldText != null } ?: "/dev/null"))
            appendLine("+++ " + patch.path)
            val before = patch.oldText.orEmpty().diffLines()
            val after = patch.newText.diffLines()
            val comparableLines = minOf(before.size, after.size)
            val prefix = (0 until comparableLines).firstOrNull { before[it] != after[it] } ?: comparableLines
            val suffix = (0 until comparableLines - prefix).firstOrNull { index ->
                before[before.lastIndex - index] != after[after.lastIndex - index]
            } ?: (comparableLines - prefix)
            val beforeEnd = before.size - suffix
            val afterEnd = after.size - suffix
            if (prefix > DiffContextLines) appendLine("⋯ ${prefix - DiffContextLines} строк пропущено")
            for (index in (prefix - DiffContextLines).coerceAtLeast(0) until prefix) {
                appendLine(" " + before[index])
            }
            val oldRange = before.subList(prefix, beforeEnd)
            val newRange = after.subList(prefix, afterEnd)
            val shared = matchingLines(oldRange, newRange)
            var oldIndex = 0
            var newIndex = 0
            while (oldIndex < oldRange.size || newIndex < newRange.size) {
                when {
                    shared != null && oldIndex < oldRange.size && newIndex < newRange.size &&
                        oldRange[oldIndex] == newRange[newIndex] -> {
                        appendLine(" " + oldRange[oldIndex++])
                        newIndex++
                    }
                    oldIndex < oldRange.size && (newIndex == newRange.size || shared == null ||
                        shared[oldIndex + 1][newIndex] >= shared[oldIndex][newIndex + 1]) -> {
                        appendLine("- " + oldRange[oldIndex++])
                        removed++
                    }
                    else -> {
                        appendLine("+ " + newRange[newIndex++])
                        added++
                    }
                }
            }
            for (index in afterEnd until minOf(after.size, afterEnd + DiffContextLines)) {
                appendLine(" " + after[index])
            }
            if (suffix > DiffContextLines) appendLine("⋯ ${suffix - DiffContextLines} строк пропущено")
            if (shared == null && oldRange.isNotEmpty() && newRange.isNotEmpty()) exactCounts = false
            if (patch.oldText != null && before == after && patch.oldText != patch.newText) {
                appendLine("⋯ Изменились переводы строк")
            }
        }
    }
    val summary = when {
        patches.any { it.scope == ToolOutput.DiffScope.AllOccurrences } -> "Все вхождения"
        patches.any { it.scope == ToolOutput.DiffScope.Replacement } -> "Фрагмент замены"
        exactCounts -> "+$added −$removed"
        else -> "Изменения"
    }
    return ChatToolDiff(first.path, body.removeSuffix("\n"), summary)
}

private fun StringBuilder.appendReceipt(text: String) {
    if (text.isBlank()) return
    appendLine("Вывод инструмента")
    for (line in text.diffLines()) appendLine("| " + line)
}

private fun matchingLines(before: List<String>, after: List<String>): Array<IntArray>? {
    if (before.isEmpty() || after.isEmpty()) return null
    if ((before.size + 1L) * (after.size + 1L) > MaxDiffCells) return null
    val lengths = Array(before.size + 1) { IntArray(after.size + 1) }
    for (oldIndex in before.indices.reversed()) {
        for (newIndex in after.indices.reversed()) {
            lengths[oldIndex][newIndex] = if (before[oldIndex] == after[newIndex]) {
                1 + lengths[oldIndex + 1][newIndex + 1]
            } else {
                maxOf(lengths[oldIndex + 1][newIndex], lengths[oldIndex][newIndex + 1])
            }
        }
    }
    return lengths
}

private fun String.diffLines(): List<String> = when {
    isEmpty() -> emptyList()
    endsWith('\n') || endsWith('\r') -> lines().dropLast(1)
    else -> lines()
}

private const val DiffContextLines = 3
private const val MaxDiffCells = 65_536
