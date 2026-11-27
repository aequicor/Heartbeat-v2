package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraMessageReference
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraReferenceSelection

internal val LocalAuroraReferenceInteraction = compositionLocalOf<AuroraReferenceInteraction?> { null }

internal class AuroraReferenceInteraction(private val chatId: String, private val dispatch: (AiStudioAction) -> Unit) {

    data class Cursor(val index: Int, val offset: Int, val afterQuote: Boolean = false)

    private val objectLabels = mutableMapOf<AuroraReferenceSelection, String>()
    private val selectionStates = linkedSetOf<SelectionState>()
    var position = Cursor(0, 0)
    private var pendingFocus by mutableStateOf<FocusRequest?>(null)
    var editorParts = 1
    var selections by mutableStateOf(emptyList<AuroraReferenceSelection>())
        private set

    fun requestFocus(cursor: Cursor, afterPartCountChange: Boolean = false) {
        pendingFocus = FocusRequest(cursor, editorParts.takeIf { afterPartCountChange })
    }

    fun requestedFocus(parts: Int): Cursor? = pendingFocus
        ?.takeIf { request -> request.previousParts != parts }
        ?.cursor?.let { cursor -> cursor.copy(index = cursor.index.coerceIn(0, parts - 1)) }

    fun focusHandled() {
        pendingFocus = null
    }

    fun rememberSelection(source: AuroraReferenceSelection, selection: SelectionState?) {
        if (source !in selections) selections = selections + source
        selection?.let(selectionStates::add)
    }

    fun toggleObject(source: AuroraReferenceSelection, label: String) {
        if (source in selections) {
            selections = selections - source
            objectLabels.remove(source)
        } else {
            selections = selections + source
            objectLabels[source] = label
        }
    }

    fun trackSelection(selection: SelectionState) {
        selectionStates.add(selection)
    }

    fun copiedSelection(): String = selections.mapNotNull { it.selectedText ?: objectLabels[it] }.joinToString("\n")

    fun hasSelection(selection: AuroraReferenceSelection?): Boolean = selections.isNotEmpty() || selection != null

    fun hasAnySelection(): Boolean = selections.isNotEmpty() || selectionStates.any { it.selectedTexts.isNotEmpty() }

    fun clearSelection() {
        selectionStates.forEach(SelectionState::clear)
        selectionStates.clear()
        objectLabels.clear()
        selections = emptyList()
    }

    fun reply(source: AuroraReferenceSelection) {
        dispatch(AiStudioAction.MessageReference.Reply(chatId, source))
        requestFocus(position)
    }

    fun quote(selection: AuroraReferenceSelection?, selectionState: SelectionState?) {
        val sources = (selections + listOfNotNull(selection)).distinct()
        if (sources.isEmpty()) return
        dispatch(AiStudioAction.MessageReference.Quote(chatId, sources, position.index, position.offset))
        selectionState?.clear()
        clearSelection()
        requestFocus(Cursor(position.index + sources.size * 2, 0, afterQuote = true), afterPartCountChange = true)
    }

    fun jump(reference: AuroraMessageReference) {
        dispatch(AiStudioAction.MessageReference.Jump(chatId, reference))
    }

    private data class FocusRequest(val cursor: Cursor, val previousParts: Int?)
}

internal val LocalAuroraReferenceRow = compositionLocalOf<AuroraReferenceRow?> { null }

internal class AuroraReferenceRow(val source: AuroraReferenceSelection, val selection: SelectionState) {

    class Region(
        val source: AuroraReferenceSelection,
        val selection: SelectionState?,
        val rawText: String,
        val objectSelectable: Boolean,
    ) {

        var bounds: Rect? = null
        var range: AuroraReferenceSelection.Range? = null
        val fragments = mutableSetOf<AuroraReferenceTextFragment>()

        fun offsetAt(point: Offset, end: Boolean): Int? = fragments.firstNotNullOfOrNull { it.offsetAt(point, end) }

        fun selectionRange(texts: List<AnnotatedString>): AuroraReferenceSelection.Range? {
            val ranges = texts.map { text ->
                fragments.mapNotNull { it.selectionRange(text) }.distinct().singleOrNull() ?: return null
            }
            return AuroraReferenceSelection.Range(ranges.minOf { it.start }, ranges.maxOf { it.endExclusive })
        }
    }

    val regions = mutableSetOf<Region>()

    fun regionAt(point: Offset): Region? =
        regions.filter { it.bounds?.contains(point) == true }.minByOrNull { region ->
            val bounds = requireNotNull(region.bounds)
            bounds.width * bounds.height
        }

    fun selected(region: Region?): AuroraReferenceSelection? {
        val state = region?.selection ?: selection
        val selectedTexts = state.selectedTexts.filterNot(AnnotatedString::isEmpty)
        if (selectedTexts.isEmpty()) return null
        val text = selectedTexts.joinToString("\n") { it.text }
        val candidates = sequenceOf(region).filterNotNull() +
            regions.asSequence().filter { it !== region && it.selection === state }
        val mapped = candidates.firstNotNullOfOrNull { candidate ->
            (candidate.range ?: candidate.selectionRange(selectedTexts))?.let {
                candidate.source.copy(range = it, selectedText = text)
            }
        }
        if (mapped != null) return mapped
        val range = region?.rawText?.indexOf(text)?.takeIf { it >= 0 }
            ?.let { AuroraReferenceSelection.Range(it, it + text.length) }
        return (region?.source ?: source).copy(range = range, selectedText = text)
    }
}
