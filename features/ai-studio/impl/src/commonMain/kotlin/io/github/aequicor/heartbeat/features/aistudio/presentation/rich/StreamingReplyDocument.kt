package io.github.aequicor.heartbeat.features.aistudio.presentation.rich

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Streaming and completed plain text use the original string and its selection/layout fast path. */
internal fun String.isLiteralReply(): Boolean =
    isEmpty() || (
        !first().isWhitespace() && !first().isDigit() && !startsWith('-') && !startsWith('+') &&
            none { it in MarkdownPunctuation } && "www." !in this && "://" !in this &&
            !lineSequence().drop(1).any { line ->
                line.startsWith(' ') || line.startsWith('-') || line.startsWith('+') ||
                    line.firstOrNull()?.isDigit() == true
            }
        )

private const val MarkdownPunctuation = "\r\t#*>|_`~[<\\&=@"

internal class StreamingReplyDocument {
    private val parser = ReplyMarkdownParser()

    fun render(id: String, text: String): LiveText =
        if (text.isLiteralReply()) LiveText(id, text) else LiveText(id, text, parser.parse(text, final = false))
}

/** A single work event shares its parser across snapshots and retains completed revisions on demand. */
internal class WorkMarkdownStream {
    private val mutex = Mutex()
    private val parser = ReplyMarkdownParser(literalFastPath = false)

    fun snapshot(source: String): Snapshot = Snapshot(source)

    internal inner class Snapshot(source: String) {
        private val parsed = lazy(LazyThreadSafetyMode.NONE) { parser.parse(source, final = false) }

        suspend fun document(): ReplyDocument = mutex.withLock { parsed.value }
    }
}
