package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyFailure
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyDocument
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyHistoryContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

private const val StreamResizeMillis = 120

internal class ArchiveReveal(
    val initialOffset: Int,
    val reserveSpace: Boolean,
    private val onPlaced: () -> Unit,
) {
    var isPlaced by mutableStateOf(false)
        private set

    fun markPlaced() {
        if (isPlaced) return
        isPlaced = true
        onPlaced()
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
    keepStartDuringResize: Boolean = false,
    anchoredHeight: AuroraMeasuredHeight? = null,
    onAnchoredHeight: (Int, Int) -> Unit = { _, _ -> },
) {
    val hasAnswer = reply.text.isNotEmpty() || reply.hasFiles
    val showAnswer = reply.status != ReplyStatus.Working || liveText != null || hasAnswer
    // Use the placed archive body as the starting size for subsequent resize animations.
    val opening = archiveReveal?.takeUnless { it.isPlaced }
    val immediate = opening != null || liveText != null || finalResize || keepStartDuringResize
    val size = when {
        anchoredHeight != null ->
            auroraMeasuredHeightMotion(anchoredHeight, StreamResizeMillis, onResizeEnd, immediate, onAnchoredHeight)
        immediate -> Modifier
        else -> Modifier.auroraHeightMotion(StreamResizeMillis, false, onResizeEnd)
    }
    val finishResize by rememberUpdatedState(onResizeEnd)
    LaunchedEffect(finalResize) {
        if (finalResize) withFrameNanos { finishResize() }
    }
    AuroraReplyReveal(liveText != null) {
        Box(size, propagateMinConstraints = true) {
            if (showAnswer) {
                AuroraReplyContent(reply, touch, onAction, liveText, selection, viewportHeight, opening)
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
    viewportHeight: Dp,
    archiveReveal: ArchiveReveal?,
) {
    val current = liveText?.collectAsState()?.value?.takeIf { it.replyId == reply.id }
    val text = if (liveText == null) reply.text else current?.text ?: reply.text
    val working = reply.status == ReplyStatus.Working
    if (working && text.isEmpty() && !reply.hasFiles) {
        if (reply.steps.isEmpty() && reply.interim == null) AuroraPendingReply(touch)
        return
    }
    val textPresent = reply.text.isNotEmpty()
    val simple = reply.historyContent?.plain != false
    val inline = liveText == null && reply.inlineAnswerTime
    val horizontalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageHorizontalInset
    val verticalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageVerticalInset
    val compact = !touch && textPresent && reply.status == ReplyStatus.Completed
    val padding = Modifier.padding(
        start = horizontalInset, end = horizontalInset,
        top = if (compact) AuroraLayout.DesktopCompactMessageTopInset else verticalInset,
    )
    AuroraMessageTimestamp(
        if (working) "" else reply.time,
        inlineAllowed = inline,
        textSetsWidth = simple && textPresent && !reply.hasFiles && liveText == null,
        modifier = padding,
        bottomPadding = if (compact) AuroraLayout.DesktopCompactMessageBottomInset else verticalInset,
        status = { AuroraReplyStatus(reply, touch) },
    ) { onTextLayout ->
        Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            AuroraReplyBlocks(
                reply, touch, onAction, current, liveText != null, selection,
                viewportHeight, archiveReveal, onTextLayout,
            )
        }
    }
}

private const val WaitingDotCount = 3
private const val WaitingIdleOpacity = 0.3f

@Composable
private fun AuroraPendingReply(touch: Boolean) {
    val surface = Modifier.widthIn(min = AuroraLayout.PendingReplyMinWidth)
        .heightIn(min = AuroraLayout.PendingReplyMinHeight).padding(AuroraLayout.Space)
    val announcement = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (!touch) {
        Box(surface, contentAlignment = Alignment.CenterStart) {
            AuroraDesktopWorkGoal("Агент работает", announcement)
        }
        return
    }
    Row(
        surface,
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(AuroraLayout.DesktopTarget)) {
            val stroke = AuroraLayout.FrameStroke.toPx()
            drawCircle(
                AuroraColors.Primary,
                radius = (AuroraLayout.ActiveMarker.toPx() - stroke) / 2,
                style = Stroke(stroke),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.WorkActivityDotsGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraWorkText("Агент работает", AuroraWorkRole.Detail, announcement)
            AuroraWorkActivityDots()
        }
    }
}

@Composable
internal fun AuroraWorkActivityDots(modifier: Modifier = Modifier) {
    val phase = if (HbTheme.motion == HbMotionPreference.Reduced) null else {
        rememberInfiniteTransition(label = "Agent activity").animateFloat(
            initialValue = 0f,
            targetValue = WaitingDotCount.toFloat(),
            animationSpec = infiniteRepeatable(tween(
                durationMillis = HbMotions.Default.deliberateTransitionMs * WaitingDotCount,
                easing = LinearEasing,
            )),
            label = "Activity pulse",
        )
    }
    Canvas(modifier.size(AuroraLayout.WorkActivityDotsWidth, AuroraLayout.Glyph)) {
        val diameter = AuroraLayout.WorkActivityDotDiameter.toPx()
        val firstCenter = AuroraLayout.WorkActivityDotStartInset.toPx() + diameter / 2
        val centerY = AuroraLayout.WorkActivityDotCenterY.toPx()
        for (index in 0 until WaitingDotCount) {
            val peak = index + 0.5f
            val emphasis = (1f - 2f * abs((phase?.value ?: 0.5f) - peak)).coerceAtLeast(0f)
            drawCircle(
                AuroraColors.Primary.copy(alpha = WaitingIdleOpacity + (1f - WaitingIdleOpacity) * emphasis),
                radius = diameter / 2,
                center = Offset(firstCenter + index * AuroraLayout.ControlGap.toPx(), centerY),
            )
        }
    }
}

private val Reply.inlineAnswerTime: Boolean
    get() = status == ReplyStatus.Completed && !hasFiles && text.isNotEmpty()

private val Reply.hasFiles: Boolean get() = result != null || artifacts.isNotEmpty()

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
    val lastReady = remember(reply.id) { DisplayedReplyRevision<AnswerFrame?>(null) }
    val previous = lastReady.value
    // Keep the streamed body in the reverse list while the same text gets its final formatting.
    val renderable = when {
        latest.preparing -> previous?.takeIf { latest.text.startsWith(it.text) } ?: latest
        LocalAuroraReplyArrival.current?.isActive?.invoke() == true && !latest.streaming &&
            previous?.streaming == true && previous.text == latest.text -> previous
        else -> latest
    }
    val frame = key(reply.id) {
        keepSelectedReplyRevision(renderable, selection) { displayed, incoming ->
            when {
                !incoming.streaming -> incoming
                displayed.isRich && incoming.isRich && incoming.text.startsWith(displayed.text) -> incoming
                else -> displayed
            }
        }
    }
    if (!frame.preparing) SideEffect { lastReady.value = frame }
    val initialOffset = archiveReveal?.initialOffset ?: 0
    val reserveSpace = archiveReveal?.reserveSpace != false
    val density = LocalDensity.current
    val preparingHeight = remember(history, viewportHeight, initialOffset, reserveSpace, density) {
        when {
            // The reverse list must be able to measure the restored offset before the rich body is placed.
            initialOffset > 0 -> viewportHeight + with(density) { initialOffset.toDp() }
            reserveSpace -> largeReplyPreparingHeight(history != null, text, viewportHeight)
            else -> Dp.Hairline
        }
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
            modifier = Modifier.trackReplyArrivalBlock(),
            touch = touch, background = AuroraColors.Canvas, round = false,
        ) {
            AuroraFileLabel(result, Modifier.padding(vertical = AuroraLayout.SmallGap))
        }
    }
    AuroraArtifactRows(reply.artifacts, reply.id, touch, onAction, width)
}

@Composable
internal fun AuroraArtifactRows(
    files: List<File>,
    replyId: String,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) return
    Column(modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
        for (file in files) key(file.id) { AuroraArtifactRow(file, replyId, touch, onAction) }
    }
}

@Composable
private fun AuroraArtifactRow(file: File, replyId: String, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    val button = Modifier.size(if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget)
    Row(Modifier.fillMaxWidth().trackReplyArrivalBlock(), verticalAlignment = Alignment.CenterVertically) {
        AuroraFileLabel(file, Modifier.weight(1f), attachmentTile = true)
        AuroraControl(
            { onAction(AiStudioAction.OpenArtifact(replyId, file.id)) },
            modifier = button,
            label = "Открыть ${file.name}", touch = touch, enabled = file.canPreview,
            background = Color.Transparent, hoverBackground = AuroraColors.Canvas, horizontalPadding = Dp.Hairline,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HbIcon(HbIconGlyph.Eye, Modifier.size(AuroraLayout.LargeGlyph))
            }
        }
        AuroraControl(
            { onAction(AiStudioAction.SaveArtifact(replyId, file.id)) },
            modifier = button,
            label = "Скачать ${file.name}", touch = touch, background = Color.Transparent,
            hoverBackground = AuroraColors.Canvas, horizontalPadding = Dp.Hairline,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HbIcon(HbIconGlyph.ArrowUp, Modifier.size(AuroraLayout.LargeGlyph).rotate(180f))
            }
        }
    }
}

@Composable
private fun completedAnswer(history: ReplyHistoryContent, text: String): AnswerFrame {
    val monospace = auroraCodeFontFamily()
    val cache = LocalAuroraCompletedDocuments.current
    val initial = remember(history, monospace, cache) {
        val available = history.available
        val prepared = available?.let { cache?.find(history, it, monospace) }
        val preparing = available?.plainText == null && prepared == null
        AnswerFrame(available, text, streaming = false, preparing = preparing, prepared = prepared)
    }
    if (initial.prepared != null) return initial
    val frame by produceState(initial, history, monospace, cache) {
        val (document, styled) = prepareCompletedDocument(history, monospace)
        if (styled != null && text.length <= MaxCachedReplyLength) cache?.store(history, document, styled)
        value = AnswerFrame(document, text, streaming = false, prepared = styled)
    }
    return frame
}

internal val LocalAuroraCompletedDocuments = staticCompositionLocalOf<AuroraCompletedDocuments?> { null }

/** Keeps styled Markdown across lazy row disposal, within the lifetime of one history viewport. */
internal class AuroraCompletedDocuments {
    private val documents = LinkedHashMap<ReplyHistoryContent, Entry>()

    fun find(history: ReplyHistoryContent, document: ReplyDocument, monospace: FontFamily): PreparedDocument? =
        documents[history]?.takeIf { it.document === document && it.styled.monospace == monospace }?.styled

    fun store(history: ReplyHistoryContent, document: ReplyDocument, styled: PreparedDocument) {
        documents[history] = Entry(document, styled)
        if (documents.size > MaxCachedReplies) documents.remove(documents.keys.first())
    }

    suspend fun prepareInitial(replies: List<ReplyHistoryContent>, monospace: FontFamily) {
        for (history in replies) {
            val available = history.available
            if (available != null && (available.plainText != null || find(history, available, monospace) != null)) continue
            val (document, styled) = prepareCompletedDocument(history, monospace)
            if (styled != null) store(history, document, styled)
        }
    }

    private class Entry(val document: ReplyDocument, val styled: PreparedDocument)
}

internal const val MaxCachedReplies = 24
internal const val MaxCachedReplyLength = 32_768

@Suppress("InjectDispatcher") // The history viewport owns background Markdown preparation.
private suspend fun prepareCompletedDocument(history: ReplyHistoryContent, monospace: FontFamily) =
    withContext(Dispatchers.Default) {
        val document = history.document()
        val styled = if (document.plainText == null) document.prepareRichDocument(monospace) else null
        document to styled
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
private fun AuroraReplyStatus(reply: Reply, touch: Boolean) {
    val alignWithMarker = if (touch) Modifier else {
        Modifier.offset(x = AuroraLayout.Space - AuroraLayout.DesktopMessageHorizontalInset)
    }
    when (reply.status) {
        ReplyStatus.Failed -> {
            val label = when (reply.failure) {
                ReplyFailure.RateLimited -> "Слишком много запросов"
                null -> "Ошибка выполнения"
            }
            AuroraText(label, alignWithMarker, small = true, color = AuroraColors.Error)
        }
        ReplyStatus.Stopped -> AuroraText(
            if (touch) "Остановлено" else "Ответ остановлен", alignWithMarker,
            small = true, color = AuroraColors.MessageMuted,
        )
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
