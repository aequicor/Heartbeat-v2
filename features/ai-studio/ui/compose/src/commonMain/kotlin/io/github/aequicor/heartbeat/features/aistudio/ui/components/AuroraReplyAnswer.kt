package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyHistoryContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow

private const val StreamResizeMillis = 120

internal class ArchiveReveal {
    var isPlaced by mutableStateOf(false)
        private set

    fun markPlaced() {
        isPlaced = true
    }
}

@Composable
internal fun AuroraReplyAnswer(
    reply: Reply,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    liveText: StateFlow<LiveText>?,
    selection: SelectionState,
    finalResize: Boolean,
    onResizeEnd: () -> Unit,
    viewportHeight: Dp = Dp.Hairline,
    archiveReveal: ArchiveReveal? = null,
) {
    val hasAnswer = reply.text.isNotEmpty() || reply.result != null
    val hasWork = reply.steps.isNotEmpty()
    val showAnswer = reply.status != ReplyStatus.Working || liveText != null || hasAnswer
    val width = if (hasWork && reply.status == ReplyStatus.Working) Modifier.fillMaxWidth() else Modifier
    // Use the placed archive body as the starting size for subsequent resize animations.
    val opening = archiveReveal?.takeUnless { it.isPlaced }
    val immediate = opening != null || liveText != null || finalResize
    val size = if (immediate) width else width.auroraHeightMotion(StreamResizeMillis, false, onResizeEnd)
    val finishResize by rememberUpdatedState(onResizeEnd)
    LaunchedEffect(finalResize) {
        if (finalResize) withFrameNanos { finishResize() }
    }
    AuroraReplyReveal(liveText != null) {
        Box(size, propagateMinConstraints = true) {
            if (showAnswer) {
                val wide = hasWork && (reply.status != ReplyStatus.Completed || !hasAnswer)
                AuroraReplyContent(reply, touch, onAction, liveText, selection, wide, viewportHeight, opening)
            }
        }
    }
}

@Composable
private fun AuroraReplyContent(
    reply: Reply,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    liveText: StateFlow<LiveText>?,
    selection: SelectionState,
    wide: Boolean,
    viewportHeight: Dp,
    archiveReveal: ArchiveReveal?,
) {
    val current = liveText?.collectAsState()?.value?.takeIf { it.replyId == reply.id }
    val text = if (liveText == null) reply.text else current?.text ?: reply.text
    val working = reply.status == ReplyStatus.Working
    // The timeline owns the thinking bubble's insets until an answer actually exists.
    if (reply.workingWithoutResult && text.isEmpty()) return
    val textPresent = reply.text.isNotEmpty()
    val simple = reply.historyContent?.plain != false
    val inline = liveText == null && reply.inlineAnswerTime
    val width = if (wide || working) Modifier.fillMaxWidth() else Modifier
    val horizontalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageHorizontalInset
    val verticalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageVerticalInset
    val padding = width.padding(
        start = horizontalInset, end = horizontalInset, top = verticalInset,
    )
    AuroraMessageTimestamp(
        if (working) "" else reply.time,
        inlineAllowed = inline,
        textSetsWidth = simple && textPresent && reply.result == null && liveText == null,
        modifier = padding,
        bottomPadding = verticalInset,
        status = { AuroraReplyStatus(reply.status) },
    ) { onTextLayout ->
        Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            AuroraReplyBlocks(
                reply, touch, onAction, current, liveText != null, selection,
                viewportHeight, archiveReveal, onTextLayout,
            )
        }
    }
}

private val Reply.workingWithoutResult: Boolean
    get() = status == ReplyStatus.Working && steps.isNotEmpty() && result == null

private val Reply.inlineAnswerTime: Boolean
    get() = historyContent?.plain != false && status == ReplyStatus.Completed && result == null && text.isNotEmpty()

@Composable
private fun AuroraReplyBlocks(
    reply: Reply,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    current: LiveText?,
    streaming: Boolean,
    selection: SelectionState,
    viewportHeight: Dp,
    archiveReveal: ArchiveReveal?,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val textWidth = if (touch) AuroraLayout.AgentText else AuroraLayout.DesktopAgentText
    val width = Modifier.widthIn(max = textWidth)
    val text = if (streaming) current?.text ?: reply.text else reply.text
    val history = reply.historyContent?.takeUnless { it.plain || streaming }
    val latest = if (history == null) {
        AnswerFrame(current?.document, text, streaming)
    } else key(history) {
        completedAnswer(history, text)
    }
    val frame = key(reply.id) {
        keepSelectedReplyRevision(latest, selection) { displayed, incoming ->
            when {
                !incoming.streaming -> incoming
                displayed.isRich && incoming.isRich && incoming.text.startsWith(displayed.text) -> incoming
                else -> displayed
            }
        }
    }
    val preparingHeight = remember(history, viewportHeight) {
        largeReplyPreparingHeight(history != null, text, viewportHeight)
    }
    if (frame.preparing) {
        AuroraPreparingReply(width, preparingHeight)
    } else if (frame.text.isNotEmpty()) {
        AuroraAnswerDocument(
            frame, touch, selection, onTextLayout, width, preparingHeight, archiveReveal,
        )
    }
    val result = reply.result
    if (result != null) {
        AuroraControl(
            { onAction(AiStudioAction.OpenResult(reply.id)) },
            touch = touch, background = AuroraColors.Canvas, round = false,
        ) {
            AuroraFileLabel(result, Modifier.padding(vertical = AuroraLayout.SmallGap))
        }
    }
}

@Composable
@Suppress("InjectDispatcher") // Parse and style the archive revision together on the worker.
private fun completedAnswer(history: ReplyHistoryContent, text: String): AnswerFrame {
    val monospace = auroraCodeFontFamily()
    val initial = remember(history) {
        val available = history.available
        AnswerFrame(available, text, streaming = false, preparing = available == null)
    }
    val frame by produceState(initial, history, monospace) {
        // Parse and style an uncached answer in one worker pass before publishing it to the UI.
        value = withContext(Dispatchers.Default) {
            val document = history.document()
            val styled = if (initial.document !== document && document.plainText == null) {
                document.prepareRichDocument(monospace)
            } else null
            AnswerFrame(document, text, streaming = false, prepared = styled)
        }
    }
    return frame
}

private class AnswerFrame(
    val document: ReplyDocument?,
    val text: String,
    val streaming: Boolean,
    val preparing: Boolean = false,
    val prepared: PreparedDocument? = null,
) {
    val isRich: Boolean get() = document != null && document.plainText == null
}

private class DisplayedReplyRevision<T>(var value: T)

/** Keep the displayed text and its native selection coordinates aligned until the selection clears. */
@Composable
internal fun <T> keepSelectedReplyRevision(
    latest: T,
    selection: SelectionState,
    mergeSelected: (displayed: T, incoming: T) -> T = { displayed, _ -> displayed },
): T {
    val revision = remember(selection) { DisplayedReplyRevision(latest) }
    val selected by remember(selection) { derivedStateOf { selection.selectedTexts.isNotEmpty() } }
    val visible = if (selected) mergeSelected(revision.value, latest) else latest
    SideEffect { revision.value = visible }
    return visible
}

@Composable
private fun AuroraReplyStatus(status: ReplyStatus) {
    when (status) {
        ReplyStatus.Failed -> AuroraText("Ошибка выполнения", small = true, color = AuroraColors.Error)
        ReplyStatus.Stopped -> AuroraText("Остановлено", small = true, color = AuroraColors.MessageMuted)
        else -> Unit
    }
}

@Composable
private fun AuroraAnswerDocument(
    frame: AnswerFrame, touch: Boolean, selection: SelectionState,
    onTextLayout: (TextLayoutResult) -> Unit, modifier: Modifier = Modifier,
    preparingHeight: Dp = Dp.Hairline,
    archiveReveal: ArchiveReveal? = null,
) {
    val document = frame.document
    val text = frame.text
    if (frame.streaming && (document == null || document.plainText != null)) {
        AuroraStreamingPlainText(document?.plainText ?: text, selection, onTextLayout, modifier)
    } else if (document == null) {
        SelectionContainer(state = selection) {
            AuroraReplyText(text, modifier, style = auroraMessageStyle(), onTextLayout = onTextLayout)
        }
    } else {
        AuroraRichDocument(
            document, touch, selection, modifier, onTextLayout,
            streaming = frame.streaming, preparingHeight = preparingHeight, prepared = frame.prepared,
            archiveReveal = archiveReveal,
        )
    }
}

private fun largeReplyPreparingHeight(hasHistory: Boolean, text: String, viewportHeight: Dp): Dp {
    if (!supportsAuroraStagedRichLayout() || !hasHistory || viewportHeight <= Dp.Hairline) return Dp.Hairline
    return if (likelyFillsHistoryViewport(text, viewportHeight)) viewportHeight else Dp.Hairline
}

private fun likelyFillsHistoryViewport(source: String, viewportHeight: Dp): Boolean {
    if (source.length < MinimumEstimatedReplyLength) return false
    val text = source.take(MaximumEstimatedPrefix)
    val required = (viewportHeight.value / EstimatedLineHeightDp).toInt().coerceAtLeast(MinimumEstimatedReplyLines)
    var start = 0
    var lines = 0
    var fence: FenceMarker? = null
    var codeLines = 0
    while (start < text.length && lines < required) {
        val end = text.indexOf('\n', start).takeUnless { it == -1 } ?: text.length
        val marker = text.fenceMarker(start, end)
        when {
            fence?.closesWith(marker) == true -> {
                lines += 2 + minOf(codeLines, MaximumEstimatedCodeLines)
                fence = null
                codeLines = 0
            }
            fence != null -> codeLines++
            marker != null -> fence = marker
            end - start > 1 -> lines += 1 + (end - start) / EstimatedLineCharacters
        }
        start = end + 1
    }
    return lines >= required
}

private class FenceMarker(val delimiter: Char, val length: Int, val hasInfo: Boolean) {
    fun closesWith(other: FenceMarker?): Boolean =
        other != null && other.delimiter == delimiter && other.length >= length && !other.hasInfo
}

private fun String.fenceMarker(start: Int, end: Int): FenceMarker? {
    var at = start
    while (at < end && at - start < FenceIndentLimit && this[at] == ' ') at++
    val marker = getOrNull(at)?.takeIf { it == '`' || it == '~' } ?: return null
    val markerStart = at
    while (at < end && this[at] == marker) at++
    val count = at - markerStart
    return if (count >= FenceMarkerMinimumLength) {
        FenceMarker(marker, count, (at until end).any { !this[it].isWhitespace() })
    } else null
}

private const val MinimumEstimatedReplyLength = 2_048
private const val MaximumEstimatedPrefix = 16_384
private const val MinimumEstimatedReplyLines = 48
private const val MaximumEstimatedCodeLines = 16
private const val EstimatedLineCharacters = 80
private const val EstimatedLineHeightDp = 18f
private const val FenceIndentLimit = 3
private const val FenceMarkerMinimumLength = 3

@Composable
internal expect fun AuroraStreamingPlainText(
    text: String,
    selection: SelectionState,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
)
