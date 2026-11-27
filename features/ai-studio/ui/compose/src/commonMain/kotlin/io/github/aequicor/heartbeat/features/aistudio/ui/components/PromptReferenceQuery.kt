package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.PromptReferences.Entry

internal data class PromptReferenceQuery(val start: Int, val end: Int, val query: String) {

    fun replace(field: TextFieldValue, reference: Entry): TextFieldValue {
        val label = reference.name.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
            .replace('\n', ' ').replace('\r', ' ')
        val uri = reference.uri.replace("<", "%3C").replace(">", "%3E").replace("\n", "%0A").replace("\r", "%0D")
        val separator = if (end == field.text.length) " " else ""
        val link = "[$label](<$uri>)$separator"
        return TextFieldValue(
            text = field.text.replaceRange(start, end, link),
            selection = TextRange(start + link.length),
        )
    }
}

internal fun TextFieldValue.promptReferenceQuery(): PromptReferenceQuery? {
    if (!selection.collapsed || composition != null) return null
    val caret = selection.end
    var start = caret
    while (start > 0 && text[start - 1].isQueryCharacter()) start--
    val at = start - 1
    if (at < 0 || text[at] != '@') return null
    val before = text.getOrNull(at - 1)
    if (before != null && !before.isWhitespace() && before !in "([{") return null
    var end = caret
    while (end < text.length && text[end].isQueryCharacter()) end++
    // A reference can be selected in the middle of a partially typed name.
    return PromptReferenceQuery(at, end, text.substring(start, caret))
}

private fun Char.isQueryCharacter(): Boolean = !isWhitespace() && this !in "@[](){}<>,;!?"
