package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult

internal actual fun supportsAuroraStagedRichLayout(): Boolean = false

@Composable
internal actual fun AuroraStreamingPlainText(
    text: String,
    selection: SelectionState,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier,
) {
    SelectionContainer(state = selection, modifier = modifier) {
        BasicText(text, style = auroraMessageStyle(), onTextLayout = onTextLayout)
    }
}
