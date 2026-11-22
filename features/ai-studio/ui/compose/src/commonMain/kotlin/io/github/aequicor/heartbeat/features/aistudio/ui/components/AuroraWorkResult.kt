package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyHistoryContent

@Composable
internal fun AuroraWorkResult(
    text: String,
    content: ReplyHistoryContent?,
    touch: Boolean,
    modifier: Modifier = Modifier,
) {
    val selection = rememberSelectionState()
    val markdown = content?.takeUnless { it.plain }
    val width = if (markdown == null) Modifier else WorkMarkdownIntrinsicSize
    SelectionContainer(state = selection, modifier = modifier) {
        Column {
            AuroraMarkdownContent(text, markdown, selection,
                auroraMessageStyle().copy(color = AuroraColors.WorkInk), touch, width)
        }
    }
}
