package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import kotlinx.coroutines.flow.StateFlow

private const val StreamResizeMillis = 120

@Composable
internal fun AuroraReplyAnswer(
    reply: Reply,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    liveText: StateFlow<LiveText>?,
    selection: SelectionState,
    finalResize: Boolean,
    onResizeEnd: () -> Unit,
) {
    val hasAnswer = reply.text.isNotEmpty() || reply.result != null
    val hasWork = reply.steps.isNotEmpty()
    val showAnswer = reply.status != ReplyStatus.Working || liveText != null || hasAnswer
    val width = if (!showAnswer && hasWork) Modifier.fillMaxWidth() else Modifier
    Box(width.auroraHeightMotion(StreamResizeMillis, finalResize, onResizeEnd), propagateMinConstraints = true) {
        if (showAnswer) {
            val wide = hasWork && (reply.status != ReplyStatus.Completed || !hasAnswer)
            AuroraReplyContent(reply, touch, onAction, liveText, selection, wide)
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
) {
    val textPresent = reply.text.isNotEmpty()
    val working = reply.status == ReplyStatus.Working
    val inline = reply.status == ReplyStatus.Completed && liveText == null && reply.result == null && textPresent
    val width = if (wide || working) Modifier.fillMaxWidth() else Modifier
    val horizontalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageHorizontalInset
    val verticalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageVerticalInset
    val padding = width.padding(
        start = horizontalInset, end = horizontalInset, top = verticalInset,
    )
    if (working) {
        Column(padding.padding(bottom = verticalInset)) {
            AuroraReplyBlocks(reply, touch, onAction, liveText, selection)
        }
        return
    }

    AuroraMessageTimestamp(
        reply.time,
        inlineAllowed = inline,
        textSetsWidth = textPresent && reply.result == null && liveText == null,
        modifier = padding,
        bottomPadding = verticalInset,
        status = { AuroraReplyStatus(reply.status) },
    ) { onTextLayout ->
        Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            AuroraReplyBlocks(reply, touch, onAction, liveText, selection, onTextLayout)
        }
    }
}

@Composable
private fun AuroraReplyBlocks(
    reply: Reply,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    liveText: StateFlow<LiveText>?,
    selection: SelectionState,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val textWidth = if (touch) AuroraLayout.AgentText else AuroraLayout.DesktopAgentText
    if (liveText != null) {
        AuroraLiveText(reply.id, liveText, selection, Modifier.widthIn(max = textWidth))
    } else if (reply.text.isNotEmpty()) {
        SelectionContainer(state = selection) {
            BasicText(
                reply.text, Modifier.widthIn(max = textWidth),
                style = auroraMessageStyle(), onTextLayout = onTextLayout,
            )
        }
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
private fun AuroraReplyStatus(status: ReplyStatus) {
    when (status) {
        ReplyStatus.Failed -> AuroraText("Ошибка выполнения", small = true, color = AuroraColors.Error)
        ReplyStatus.Stopped -> AuroraText("Остановлено", small = true, color = AuroraColors.MessageMuted)
        else -> Unit
    }
}

@Composable
private fun AuroraLiveText(
    replyId: String,
    stream: StateFlow<LiveText>,
    selection: SelectionState,
    modifier: Modifier = Modifier,
) {
    val current by stream.collectAsState()
    if (current.replyId == replyId && current.text.isNotEmpty()) {
        SelectionContainer(state = selection) {
            BasicText(current.text, modifier, style = auroraMessageStyle())
        }
    }
}
