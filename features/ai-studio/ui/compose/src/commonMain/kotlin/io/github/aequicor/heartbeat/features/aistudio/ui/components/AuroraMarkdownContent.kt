package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyHistoryContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses the selection container of the surrounding detail or file viewer. */
@Composable
@Suppress("InjectDispatcher") // The displayed revision owns one background pass for parsing and styling.
internal fun AuroraMarkdownContent(
    source: String,
    content: ReplyHistoryContent?,
    selection: SelectionState,
    style: TextStyle,
    touch: Boolean,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    val monospace = auroraCodeFontFamily()
    var prepared by remember { mutableStateOf<MarkdownRevision?>(null) }
    LaunchedEffect(content, monospace) {
        val previous = prepared?.document?.takeIf { it.monospace == monospace }?.blocks.orEmpty()
        prepared = if (content == null) null else withContext(Dispatchers.Default) {
            val parsed = content.document()
            val plain = parsed.plainText
            if (plain != null) MarkdownRevision(source, plain)
            else MarkdownRevision(source, null, parsed.prepareRichDocument(monospace, previous))
        }
    }
    val ready = remember(prepared, source, content) {
        prepared?.takeIf { source.startsWith(it.source) }
            ?: MarkdownRevision(source, source.takeIf { content == null })
    }
    val displayed = keepSelectedReplyRevision(ready, selection) { previous, incoming ->
        if (previous.document != null && incoming.document != null && incoming.source.startsWith(previous.source)) {
            incoming
        } else {
            previous
        }
    }
    val blocks = selectedPreparedDocument(displayed.document, selection)?.blocks
    when {
        blocks != null -> Box(modifier) {
            AuroraDocumentBlocks(blocks, touch, streaming, Dp.Hairline, bodyStyle = style)
        }
        displayed.text != null -> BasicText(displayed.text, modifier, style)
        else -> DisableSelection { AuroraPreparingReply(modifier) }
    }
}

private class MarkdownRevision(val source: String, val text: String?, val document: PreparedDocument? = null)
