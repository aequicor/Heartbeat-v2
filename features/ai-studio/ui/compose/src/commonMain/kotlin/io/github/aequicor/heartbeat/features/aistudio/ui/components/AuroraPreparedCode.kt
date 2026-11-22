package io.github.aequicor.heartbeat.features.aistudio.ui.components

import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyCodeLine
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyCodeKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AuroraPreparedCode(
    val highlights: List<ReplyCodeLine>?,
    val lines: List<AnnotatedString>,
    val longestLine: Int,
    val compact: List<AnnotatedString>?,
)

internal suspend fun prepareAuroraCode(
    source: String,
    highlights: List<ReplyCodeLine>?,
    previous: AuroraPreparedCode?,
): AuroraPreparedCode {
    val context = currentCoroutineContext()
    var longest = 0
    val lines = if (highlights == null) {
        source.lineSequence().map { line ->
            context.ensureActive()
            longest = maxOf(longest, line.length)
            AnnotatedString(line)
        }.toList()
    } else highlights.mapIndexed { index, line ->
        context.ensureActive()
        longest = maxOf(longest, line.text.length)
        if (previous?.highlights?.getOrNull(index) === line) {
            return@mapIndexed previous.lines[index]
        }
        buildAnnotatedString {
            append(line.text)
            for (highlight in line.highlights) addStyle(highlight.kind.style, highlight.start, highlight.end)
        }
    }
    val compact = if (lines.size <= ShortCodeLineLimit && source.length <= ShortCodeCharacterLimit) {
        lines.chunked(CodeRowsPerItem) { group ->
            if (group.size == 1) group.first() else buildAnnotatedString {
                group.forEachIndexed { position, line ->
                    if (position > 0) append('\n')
                    append(line)
                }
            }
        }
    } else null
    return AuroraPreparedCode(highlights, lines, longest, compact)
}

@Suppress("MagicNumber")
private val ReplyCodeKind.style: SpanStyle
    get() = when (this) {
        ReplyCodeKind.Keyword -> SpanStyle(color = Colors.SyntaxKeyword)
        ReplyCodeKind.String -> SpanStyle(color = Colors.SyntaxString)
        ReplyCodeKind.Comment -> SpanStyle(color = Colors.SyntaxComment)
        ReplyCodeKind.Number -> SpanStyle(color = Colors.SyntaxNumber)
        ReplyCodeKind.DiffAdd -> SpanStyle(color = Colors.DiffAddedInk, background = Colors.DiffAddedSurface)
        ReplyCodeKind.DiffDelete -> SpanStyle(color = Colors.DiffDeletedInk, background = Colors.DiffDeletedSurface)
    }

private const val ShortCodeLineLimit = 32
private const val ShortCodeCharacterLimit = 16_384
private const val CodeRowsPerItem = 6
