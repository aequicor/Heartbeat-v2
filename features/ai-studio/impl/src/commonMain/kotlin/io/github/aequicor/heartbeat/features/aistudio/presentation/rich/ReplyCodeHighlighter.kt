package io.github.aequicor.heartbeat.features.aistudio.presentation.rich

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeHighlight
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyCodeLine

private const val TokenPattern = """["']|\b(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?)\b|[a-zA-Z_][a-zA-Z_0-9]*"""
private val cTokens = Regex("""//|/\*|$TokenPattern""")
private val hashTokens = Regex("""#|$TokenPattern""")
private val sqlTokens = Regex("""--|/\*|$TokenPattern""")
private val jsonTokens = Regex(TokenPattern)

private val cFamily = setOf(
    "c", "cpp", "c++", "csharp", "cs", "go", "java", "js", "javascript", "jsx", "kotlin", "kt",
    "kts", "rs", "rust", "swift", "ts", "typescript", "tsx",
)
private val hashComments = setOf("bash", "fish", "py", "python", "rb", "ruby", "sh", "yaml", "yml", "zsh")
private val sql = setOf("sql", "sqlite", "postgresql")
private val jsonKeywords = setOf("true", "false", "null")
private val keywords = (
    "abstract as assert async await break case catch class const continue data def default defer do else enum " +
        "export extends false final finally fn for from fun func function if implements import in interface internal " +
        "is let match module new nil none null object open override package private protected public return self " +
        "static struct super suspend switch this throw trait true try type typeof use val var void when while with yield"
    ).split(' ').toSet()
private val sqlKeywords = (
    "and as asc by case create delete desc distinct drop else end exists from group having in insert into is join " +
        "left limit not null on or order outer primary right select set table then union unique update values when where"
    ).split(' ').toSet()

internal fun highlightReplyCode(source: String, language: String?): List<ReplyCodeLine> {
    if (source.isEmpty()) return listOf()
    val lines = source.split("\r\n", "\r", "\n").let { if (it.last().isEmpty()) it.dropLast(1) else it }
    val highlighter = ReplyCodeHighlighter(language)
    return lines.map { highlighter.line(it) }
}

internal class ReplyCodeHighlighter(private val language: String?) {
    private val grammar = when (language) {
        in cFamily -> cTokens
        in hashComments -> hashTokens
        in sql -> sqlTokens
        "json" -> jsonTokens
        else -> null
    }
    private var blockComment = false

    /** A provisional line can be rendered repeatedly without changing the state of previous lines. */
    fun line(text: String, commit: Boolean = true): ReplyCodeLine {
        val highlights = when {
            language == "diff" || language == "patch" -> text.diffHighlight()
            grammar != null -> highlightLine(text, grammar, commit)
            else -> listOf()
        }
        return ReplyCodeLine(text, highlights)
    }

    private fun highlightLine(text: String, tokens: Regex, commit: Boolean): List<ReplyCodeHighlight> {
        var inComment = blockComment
        var cursor = 0
        val highlights = buildList {
            while (cursor < text.length) {
                val token = if (inComment) null else (tokens.find(text, cursor) ?: break)
                val start = if (token == null) cursor else token.range.first
                if (token != null) cursor = token.range.last + 1
                val kind = when (val marker = token?.value) {
                    "//", "#", "--" -> {
                        cursor = text.length
                        ReplyCodeKind.Comment
                    }
                    null, "/*" -> {
                        val end = text.indexOf("*/", cursor)
                        inComment = end < 0
                        cursor = if (inComment) text.length else end + 2
                        ReplyCodeKind.Comment
                    }
                    "\"", "'" -> {
                        cursor = text.quoteEnd(cursor, marker.first())
                        ReplyCodeKind.String
                    }
                    else -> marker.wordKind(language)
                }
                if (kind != null) add(ReplyCodeHighlight(start, cursor, kind))
            }
        }
        if (commit) blockComment = inComment
        return highlights
    }
}

private fun String.quoteEnd(from: Int, quote: Char): Int {
    var cursor = from
    while (cursor < length) {
        when (this[cursor++]) {
            '\\' -> cursor++
            quote -> return cursor
        }
    }
    return length
}

private fun String.diffHighlight(): List<ReplyCodeHighlight> {
    val kind = when {
        startsWith('+') && !startsWith("+++ ") -> ReplyCodeKind.DiffAdd
        startsWith('-') && !startsWith("--- ") -> ReplyCodeKind.DiffDelete
        else -> return listOf()
    }
    return listOf(ReplyCodeHighlight(0, length, kind))
}

private fun String.wordKind(language: String?): ReplyCodeKind? = when {
    first().isDigit() -> ReplyCodeKind.Number
    language in sql && lowercase() in sqlKeywords -> ReplyCodeKind.Keyword
    language == "json" && this in jsonKeywords -> ReplyCodeKind.Keyword
    language != "json" && language !in sql && lowercase() in keywords -> ReplyCodeKind.Keyword
    else -> null
}
