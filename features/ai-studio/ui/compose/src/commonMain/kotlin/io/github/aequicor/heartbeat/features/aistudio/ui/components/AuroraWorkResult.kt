package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyHistoryContent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraReferenceSelection.Kind

@Composable
internal fun AuroraWorkResult(
    text: String,
    content: ReplyHistoryContent?,
    touch: Boolean,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.WorkInk,
    reveal: Boolean = false,
    animateOnEntry: Boolean = false,
    stepId: String? = null,
) {
    val selection = rememberSelectionState()
    val markdown = content?.takeUnless { it.plain }
    val width = if (markdown == null) Modifier else WorkMarkdownIntrinsicSize
    val source = LocalAuroraReferenceRow.current?.source?.copy(kind = Kind.InterimResult, stepId = stepId)
    AuroraReferenceRegion(source, text, selection, modifier) {
        SelectionContainer(state = selection) {
            val body: @Composable () -> Unit = {
                Column {
                    AuroraMarkdownContent(text, markdown, selection,
                        auroraMessageStyle().copy(color = color), touch, width)
                }
            }
            if (reveal) AuroraWorkReveal(animateOnEntry, content = body) else body()
        }
    }
}
