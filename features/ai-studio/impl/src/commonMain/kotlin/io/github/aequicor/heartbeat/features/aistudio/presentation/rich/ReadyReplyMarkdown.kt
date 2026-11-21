package io.github.aequicor.heartbeat.features.aistudio.presentation.rich

/** Keeps only ambiguous trailing syntax out of the live preview. The saved source is never truncated. */
internal fun readyReplyMarkdown(source: String): String = source.substring(0, ReplyMarkdownBoundary(source).end())

private class ReplyMarkdownBoundary(private val source: String) {
    private data class Delimiter(val marker: String, val start: Int)
    private val delimiters = mutableListOf<Delimiter>()
    private var cursor = 0
    private var fence: String? = null
    private var pending = source.length

    @Suppress("CyclomaticComplexMethod") // Keep the character dispatch with the scanner loop.
    fun end(): Int {
        while (cursor < source.length) {
            if (cursor == 0 || source[cursor - 1] == '\n') {
                if (line()) continue
            }
            when (source[cursor]) {
                '\\', '!' -> {
                    if (cursor == source.lastIndex) pending = minOf(pending, cursor)
                    cursor = minOf(cursor + if (source[cursor] == '\\') 2 else 1, source.length)
                }
                '`' -> code()
                '[' -> link()
                '*', '_', '~' -> emphasis()
                '<' -> angle()
                '&' -> entity()
                else -> cursor++
            }
        }
        return minOf(pending, delimiters.minOfOrNull { it.start } ?: source.length)
    }

    private fun line(): Boolean {
        val end = source.indexOf('\n', cursor).takeIf { it >= 0 } ?: source.length
        val text = source.substring(cursor, end).removeSuffix("\r")
        val marker = Fence.matchEntire(text)
        val current = fence
        if (current != null) {
            if (marker.closesFence(current)) fence = null
            cursor = if (end < source.length) end + 1 else end
            return true
        }
        if (text.isBlank()) delimiters.clear()
        if (marker != null) {
            if (end == source.length) pending = minOf(pending, cursor) else fence = marker.groupValues[1]
            cursor = if (end < source.length) end + 1 else end
        }
        return marker != null
    }

    private fun code() {
        val start = cursor
        val marker = run()
        var next = source.indexOf(marker, cursor)
        while (next >= 0 && (source.getOrNull(next - 1) == '`' || source.getOrNull(next + marker.length) == '`')) {
            next = source.indexOf(marker, next + marker.length)
        }
        if (next < 0) hold(start) else cursor = next + marker.length
    }

    private fun link() {
        val start = if (source.getOrNull(cursor - 1) == '!') cursor - 1 else cursor
        val label = balanced(cursor, '[', ']')
        if (label == null) { hold(start); return }
        cursor = label
        when (source.getOrNull(cursor)) {
            null -> hold(start)
            '(' -> balanced(cursor, '(', ')')?.let { cursor = it } ?: hold(start)
            '[' -> balanced(cursor, '[', ']')?.let { cursor = it } ?: hold(start)
        }
    }

    private fun balanced(start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var at = start
        while (at < source.length) {
            when (source[at]) {
                '\\' -> at++
                open -> depth++
                close -> if (--depth == 0) return at + 1
            }
            at++
        }
        return null
    }

    private fun emphasis() {
        val start = cursor
        val marker = run()
        if (marker.first() == '~' && marker.length < 2) {
            if (cursor == source.length) pending = minOf(pending, start)
            return
        }
        val before = source.getOrNull(start - 1)
        val after = source.getOrNull(cursor)
        val intraword = marker.first() == '_' && before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true
        if (intraword) return
        val remaining = if (before?.isWhitespace() == false) closeEmphasis(marker) else marker.length
        if (remaining > 0 && (after == null || !after.isWhitespace())) {
            delimiters += Delimiter(marker.take(remaining), cursor - remaining)
        }
    }

    private fun closeEmphasis(marker: String): Int {
        var remaining = marker.length
        while (remaining > 0) {
            val index = delimiters.indexOfLast { it.marker.first() == marker.first() }
            if (index < 0) break
            val opening = delimiters[index]
            val consumed = minOf(remaining, opening.marker.length)
            delimiters.subList(index, delimiters.size).clear()
            if (consumed < opening.marker.length) {
                delimiters += opening.copy(marker = opening.marker.dropLast(consumed))
            }
            remaining -= consumed
        }
        return remaining
    }

    private fun run(): String {
        val start = cursor++
        while (source.getOrNull(cursor) == source[start]) cursor++
        return source.substring(start, cursor)
    }

    private fun angle() {
        val start = cursor++
        while (cursor < source.length && source[cursor] != '>' && !source[cursor].isWhitespace()) cursor++
        if (cursor == source.length) hold(start) else cursor++
    }

    private fun entity() {
        val start = cursor++
        while (cursor < source.length && (source[cursor].isLetterOrDigit() || source[cursor] == '#')) cursor++
        if (cursor == source.length) hold(start) else if (source[cursor] == ';') cursor++
    }

    private fun hold(start: Int) {
        pending = minOf(pending, start)
        cursor = source.length
    }

    private companion object {
        val Fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    }
}

private fun MatchResult?.closesFence(current: String): Boolean {
    if (this == null) return false
    return groupValues[1].first() == current.first() &&
        groupValues[1].length >= current.length && groupValues[2].isBlank()
}
