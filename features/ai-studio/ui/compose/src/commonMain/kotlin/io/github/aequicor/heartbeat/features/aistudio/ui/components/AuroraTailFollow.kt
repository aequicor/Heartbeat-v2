package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val BurstReplyViewportPercent = 60

/** One owner for the viewport's bottom attachment, across live/history and successive replies. */
internal class AuroraTailFollow(private val list: LazyListState) {
    var following by mutableStateOf(list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0)
        private set
    var inputGeneration = 0
        private set
    private var correcting = false
    private var newestId: String? = null
    private var incomingReplyId by mutableStateOf<String?>(null)
    private var incomingReplyHeight = 0
    private var showedAnswer = false
    private var replyArrival by mutableStateOf<String?>(null)
    private var replyStart by mutableStateOf<String?>(null)
    private var requestedStartOffset: Int? = null
    private var beforeStartPlacement: LazyListLayoutInfo? = null

    data class BeforeResize(val following: Boolean, val index: Int, val offset: Int, val key: Any?, val input: Int)

    fun beforeResize() = BeforeResize(
        following, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, firstKey(), inputGeneration,
    )

    fun resized(id: String, delta: Int, before: BeforeResize, scope: CoroutineScope, selected: Boolean) {
        if (before.input != inputGeneration || id == replyStart) return
        if (before.following && !selected) correcting = true
        val reading = (!before.following || selected) && before.key == id
        val offset = before.offset + delta
        if (reading && !list.isScrollInProgress) list.requestScrollToItem(before.index, offset)
        // Child onSizeChanged runs inside the parent's measure. Reapply an unconsumed absolute
        // request after it commits; finalization may not otherwise schedule another measure.
        scope.launch {
            if (before.input == inputGeneration && id != replyStart) completeResize(id, delta, before, selected)
        }
    }

    private fun completeResize(id: String, delta: Int, before: BeforeResize, selected: Boolean) {
        val offset = before.offset + delta
        when {
            before.following && !selected -> {
                correcting = list.canScrollBackward
                if (list.canScrollBackward) list.requestScrollToItem(0)
            }
            before.key == id -> {
                if (list.isScrollInProgress) {
                    // Keep the user's wheel/fling mutation and compensate only the reverse-list origin.
                    if ((!following || selected) && firstKey() == id) list.dispatchRawDelta(delta.toFloat())
                } else if (list.firstVisibleItemIndex != before.index ||
                    list.firstVisibleItemScrollOffset != offset) {
                    list.requestScrollToItem(before.index, offset)
                }
            }
        }
    }

    fun userScroll() {
        inputGeneration++
        correcting = false
        following = false
        replyArrival = null
        replyStart = null
    }

    fun observePosition() {
        if (!list.canScrollBackward) {
            if (!list.isScrollInProgress) following = true
            correcting = false
        } else if (!correcting) following = false
    }

    fun newestChanged(id: String?, precedingId: String?, beforePrompt: String?, isReply: Boolean, arrivedAfterOpening: Boolean) {
        if (id == newestId) return
        val previous = newestId
        incomingReplyId = id.takeIf {
            isReply && if (previous == null) arrivedAfterOpening else precedingId == previous || beforePrompt == previous
        }
        incomingReplyHeight = 0
        showedAnswer = false
        replyArrival = null
        replyStart = null
        newestId = id
    }

    fun observeReplyHeight(id: String, height: Int, viewportHeight: Int, hasAnswer: Boolean, completedAnswer: Boolean) {
        if (viewportHeight <= 0) return
        if (id != replyStart) {
            if (id != incomingReplyId) return
            val growth = height - incomingReplyHeight
            incomingReplyHeight = height
            if (!hasAnswer) return
            val preview = previewHeight(viewportHeight)
            if (!showedAnswer && (completedAnswer || growth > preview / 4) && following && !list.isScrollInProgress) {
                replyArrival = id
            }
            showedAnswer = true
            if (growth <= preview) return
            incomingReplyId = null
            if (!following || list.isScrollInProgress) return
            userScroll()
            replyStart = id
            replyArrival = id
            requestedStartOffset = null
        }
        // Leave the top of the viewport available for the messages preceding the new reply.
        val offset = (height - previewHeight(viewportHeight)).coerceAtLeast(0)
        if (offset != requestedStartOffset) {
            requestedStartOffset = offset
            // Drawing must wait for the layout that consumes the request. The actual
            // offset can be clamped in a chat with little history above this reply.
            beforeStartPlacement = list.layoutInfo
            list.requestScrollToItem(0, offset)
        }
    }

    fun releaseReplyStart(id: String) {
        if (replyStart == id) replyStart = null
        if (replyArrival == id) replyArrival = null
    }

    fun keepsReplyStart(id: String): Boolean = id == replyStart

    fun revealsReply(id: String): Boolean =
        id == replyArrival && (replyStart != id || list.layoutInfo !== beforeStartPlacement)

    fun waitsForReplyArrival(id: String, rowHeight: Int, animate: Boolean, completedAnswer: Boolean): Boolean {
        if (replyArrival == id) return replyStart == id && list.layoutInfo === beforeStartPlacement
        if (incomingReplyId != id || !following || list.isScrollInProgress) return false
        val layout = list.layoutInfo
        val height = layout.viewportSize.height - layout.beforeContentPadding - layout.afterContentPadding
        val preview = previewHeight(height)
        val firstReveal = animate && !showedAnswer
        if (firstReveal && completedAnswer) return true
        val threshold = if (firstReveal) preview / 4 else preview
        return height > 0 && rowHeight - incomingReplyHeight > threshold
    }

    fun previewHeight(viewportHeight: Int): Int = viewportHeight * BurstReplyViewportPercent / 100

    fun returnToTail() {
        inputGeneration++
        replyArrival = null
        replyStart = null
        following = true
        correcting = list.canScrollBackward
        list.requestScrollToItem(0)
    }

    fun followOnSend(maximumDistance: Int) {
        val latest = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
        if (!list.canScrollBackward || latest != null && -latest.offset <= maximumDistance) returnToTail()
    }

    private fun firstKey() = list.layoutInfo.visibleItemsInfo.firstOrNull {
        it.index == list.firstVisibleItemIndex
    }?.key
}
