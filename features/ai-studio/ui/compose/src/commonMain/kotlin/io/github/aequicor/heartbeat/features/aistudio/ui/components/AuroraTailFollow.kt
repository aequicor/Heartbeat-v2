package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One owner for the viewport's bottom attachment, across live/history and successive replies. */
internal class AuroraTailFollow(private val list: LazyListState) {
    var following by mutableStateOf(list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0)
        private set
    private var inputGeneration = 0
    private var correcting = false

    data class BeforeResize(val following: Boolean, val index: Int, val offset: Int, val key: Any?, val input: Int)

    fun beforeResize() = BeforeResize(
        following, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, firstKey(), inputGeneration,
    )

    fun resized(id: String, delta: Int, before: BeforeResize, scope: CoroutineScope, selected: Boolean) {
        if (before.input != inputGeneration) return
        if (before.following && !selected) correcting = true
        val reading = (!before.following || selected) && before.key == id
        val offset = before.offset + delta
        if (reading && !list.isScrollInProgress) list.requestScrollToItem(before.index, offset)
        // Child onSizeChanged runs inside the parent's measure. Reapply an unconsumed absolute
        // request after it commits; finalization may not otherwise schedule another measure.
        scope.launch {
            if (before.input == inputGeneration) completeResize(id, delta, before, selected)
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
    }

    fun observePosition() {
        if (!list.canScrollBackward) {
            if (!list.isScrollInProgress) following = true
            correcting = false
        } else if (!correcting) following = false
    }

    fun returnToTail() {
        inputGeneration++
        following = true
        correcting = list.canScrollBackward
        list.requestScrollToItem(0)
    }

    private fun firstKey() = list.layoutInfo.visibleItemsInfo.firstOrNull {
        it.index == list.firstVisibleItemIndex
    }?.key
}
