package io.github.aequicor.heartbeat.features.aistudio.ui.components

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AttachmentSearchHits private constructor(
    private val rows: List<String>,
    private val query: String,
    private val totals: IntArray,
) {

    val size: Int get() = totals.lastOrNull() ?: 0

    fun isNotEmpty(): Boolean = size > 0

    fun getOrNull(index: Int): Hit? {
        if (index !in 0 until size) return null
        var low = 0
        var high = totals.lastIndex
        while (low < high) {
            val midpoint = (low + high) ushr 1
            if (totals[midpoint] <= index) low = midpoint + 1 else high = midpoint
        }
        val row = low
        val preceding = if (row == 0) 0 else totals[row - 1]
        var offset = rows[row].indexOf(query, ignoreCase = true)
        repeat(index - preceding) {
            offset = rows[row].indexOf(query, offset + query.length, ignoreCase = true)
        }
        return Hit(row, offset)
    }

    data class Hit(val row: Int, val offset: Int)

    companion object {

        val Empty = AttachmentSearchHits(emptyList(), "", IntArray(0))

        suspend fun search(rows: List<String>, query: String): AttachmentSearchHits {
            require(query.isNotEmpty())
            val context = currentCoroutineContext()
            var count = 0
            val totals = IntArray(rows.size)
            rows.forEachIndexed { index, text ->
                context.ensureActive()
                var found = text.indexOf(query, ignoreCase = true)
                while (found >= 0) {
                    count++
                    found = text.indexOf(query, found + query.length, ignoreCase = true)
                }
                totals[index] = count
            }
            return AttachmentSearchHits(rows, query, totals)
        }
    }
}
