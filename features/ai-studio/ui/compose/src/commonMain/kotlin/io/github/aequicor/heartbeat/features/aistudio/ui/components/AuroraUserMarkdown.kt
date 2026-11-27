package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyHistoryContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@Suppress("InjectDispatcher") // The composition owns preparation of the styled message.
internal fun AuroraUserMarkdown(
    text: String,
    markdown: ReplyHistoryContent,
    touch: Boolean,
    measureTimestampTail: Boolean,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    if (!markdown.plain) {
        val monospace = auroraCodeFontFamily()
        var blocks by remember(markdown, monospace) { mutableStateOf<List<PreparedBlock>?>(null) }
        LaunchedEffect(markdown, monospace) {
            blocks = withContext(Dispatchers.Default) {
                val document = markdown.document()
                if (document.plainText == null) document.prepareRichDocument(monospace).blocks else emptyList()
            }
        }
        val ready = blocks ?: return
        if (ready.isNotEmpty()) {
            AuroraRichBlocks(ready, touch, streaming = false, measureTimestampTail = measureTimestampTail)
            return
        }
    }
    val rendered = remember(text) { PromptLinkTransformation(text).rendered }
    if (measureTimestampTail) {
        AuroraTimestampParagraph(rendered, Modifier, auroraMessageStyle(), onTextLayout)
    } else {
        AuroraReplyText(rendered, onTextLayout = onTextLayout)
    }
}
