package io.github.aequicor.heartbeat.features.aistudio.presentation.rich

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
