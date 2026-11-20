package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

@Composable
internal fun auroraCodeFontFamily(): FontFamily = HbTypography.Code.fontFamily ?: FontFamily.Monospace

@Composable
internal fun rememberPreparedDocument(document: ReplyDocument, monospace: FontFamily): PreparedDocument? {
    var ready by remember { mutableStateOf<PreparedDocument?>(null) }
    LaunchedEffect(DocumentRevision(document), monospace) {
        val previous = ready?.takeIf { it.monospace == monospace }?.blocks.orEmpty()
        ready = withContext(Dispatchers.Default) { document.prepareRichDocument(monospace, previous) }
    }
    return ready
}

internal suspend fun ReplyDocument.prepareRichDocument(
    monospace: FontFamily, previous: List<PreparedBlock> = listOf(),
): PreparedDocument = coroutineScope {
    PreparedDocument(monospace, prepareBlocks(blocks, previous, monospace))
}

private class DocumentRevision(private val document: ReplyDocument) {
    override fun equals(other: Any?) = other is DocumentRevision && document === other.document

    override fun hashCode() = 0
}
