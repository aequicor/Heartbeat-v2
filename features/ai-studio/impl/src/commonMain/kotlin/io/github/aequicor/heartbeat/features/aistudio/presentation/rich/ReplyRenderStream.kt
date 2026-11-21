package io.github.aequicor.heartbeat.features.aistudio.presentation.rich

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import kotlinx.coroutines.Job

/** Called only by the store command loop; its timer must re-enter that same loop. */
internal class ReplyRenderStream(
    private val schedule: (Long, () -> Unit) -> Job,
    private val publish: (LiveText) -> Unit,
) {
    private var owner: Any? = null
    private var replyId: String? = null
    private var generation = 0
    private var timer: Job? = null
    private var parser = ReplyMarkdownParser()
    private var shown = ""
    private var pending: String? = null

    fun update(turn: Any, id: String, source: String) {
        if (owner !== turn || replyId != id) {
            reset()
            owner = turn
            replyId = id
        }
        val ready = readyReplyMarkdown(source)
        if (ready == shown || ready == pending) return
        if (ready.isEmpty() || (shown.isEmpty() && ready.isBlank())) return
        pending = ready
        if (shown.isEmpty()) {
            flush()
        } else if (timer == null) {
            val expected = generation
            timer = schedule(WindowMillis) {
                if (generation == expected) {
                    timer = null
                    flush()
                }
            }
        }
    }

    private fun flush() {
        val text = pending ?: return
        pending = null
        shown = text
        publish(parser.renderReadyText(checkNotNull(replyId), text))
    }

    fun finish(source: String): ReplyDocument {
        val current = parser
        reset()
        return current.parse(source, final = true)
    }

    fun reset() {
        generation++
        timer?.cancel()
        timer = null
        pending = null
        shown = ""
        owner = null
        replyId = null
        parser = ReplyMarkdownParser()
    }

    private companion object {
        const val WindowMillis = 32L
    }
}
