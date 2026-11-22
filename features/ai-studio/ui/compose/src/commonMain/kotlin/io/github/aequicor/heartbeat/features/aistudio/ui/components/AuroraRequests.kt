@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Request

@Composable
internal fun AuroraRequestPanel(
    request: Request,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    inlineStop: Boolean = true,
) {
    val questions = request as? Request.Questions
    val listening = questions?.timed == true && !questions.frozen
    val currentAction by rememberUpdatedState(onAction)
    val interaction = remember(request.id, listening) {
        var sent = false
        {
            if (listening && !sent) {
                sent = true
                currentAction(AiStudioAction.InteractWithRequest(request.id))
            }
        }
    }
    val stop = { currentAction(AiStudioAction.CancelTurn) }
    val shape = RoundedCornerShape(AuroraLayout.ContentInset)
    Column(
        modifier.fillMaxWidth().clip(shape).background(AuroraColors.Request)
            .border(AuroraLayout.Stroke, AuroraColors.RequestTrack, shape)
            .auroraRequestInteraction(request.id, interaction, stop),
    ) {
        AuroraRequestTimer(questions)
        when (request) {
            is Request.Permission -> AuroraPermission(request, touch, onAction, inlineStop)
            is Request.Questions -> AuroraQuestions(request, touch, onAction, inlineStop)
        }
    }
}

private fun Modifier.auroraRequestInteraction(
    id: String,
    interact: () -> Unit,
    stop: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    interact()
    if (event.key == Key.Escape) {
        stop()
        true
    } else false
}.onFocusChanged { if (it.hasFocus) interact() }.focusGroup()
    .pointerInput(id, interact) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent(PointerEventPass.Initial).type) {
                    PointerEventType.Enter, PointerEventType.Press, PointerEventType.Scroll -> interact()
                    else -> Unit
                }
            }
        }
    }

@Composable
private fun AuroraRequestTimer(questions: Request.Questions?) {
    val remaining = questions?.remaining ?: 1f
    val name = when {
        questions?.timed != true -> "Без ограничения времени"
        questions.frozen -> "Таймер остановлен"
        else -> "Оставшееся время"
    }
    Box(
        Modifier.fillMaxWidth().height(AuroraLayout.TimerThickness).background(AuroraColors.RequestTrack)
            .semantics {
                contentDescription = name
                if (questions?.timed == true) progressBarRangeInfo = ProgressBarRangeInfo(remaining, 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxWidth(remaining).height(AuroraLayout.TimerThickness).background(AuroraColors.Amber))
    }
}

@Composable
private fun AuroraPermission(
    request: Request.Permission,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    inlineStop: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(AuroraLayout.RequestPadding),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.ContentInset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuroraRequestTitle(request.action, Modifier.weight(1f))
            if (inlineStop) AuroraRequestStop({ onAction(AiStudioAction.CancelTurn) }, touch)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            listOf(request.target, request.arguments, request.scope).filter(String::isNotBlank).forEach { detail ->
                Box(Modifier.heightIn(min = AuroraLayout.RequestDetailLine), contentAlignment = Alignment.CenterStart) {
                    AuroraText(detail, color = AuroraColors.RequestSecondary)
                }
            }
        }
        AuroraPermissionActions(request, touch, onAction)
    }
}

@Composable
private fun AuroraQuestions(
    panel: Request.Questions,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    inlineStop: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(if (touch) AuroraLayout.RequestPadding else AuroraLayout.ContentInset),
        verticalArrangement = Arrangement.spacedBy(if (touch) AuroraLayout.ContentInset else AuroraLayout.Space),
    ) {
        AuroraQuestionHeader(panel, touch, onAction, inlineStop)
        panel.error?.let { error ->
            AuroraRequestLabel(
                error, color = AuroraColors.RequestError,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (panel.question.options.isNotEmpty()) {
            AuroraQuestionOptions(panel, touch, onAction, Modifier.weight(1f, fill = false))
        }
        AuroraQuestionActions(panel, touch, onAction)
    }
}

@Composable
private fun AuroraQuestionHeader(
    panel: Request.Questions,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    inlineStop: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hasPager = panel.questions.size > 1 || !panel.question.required
        val inlinePager = maxWidth >= AuroraLayout.RequestHorizontalHeader && HbTheme.textScale <= 1.35f
        Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuroraRequestTitle(
                    panel.question.title, Modifier.weight(1f), required = panel.question.required, compact = !touch,
                )
                if (hasPager && inlinePager) AuroraRequestNavigation(panel, touch, onAction)
                if (inlineStop) AuroraRequestStop({ onAction(AiStudioAction.CancelTurn) }, touch)
            }
            if (hasPager && !inlinePager) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    AuroraRequestNavigation(panel, touch, onAction)
                }
            }
        }
    }
}

@Composable
private fun AuroraRequestTitle(
    title: String, modifier: Modifier = Modifier, required: Boolean = false, compact: Boolean = false,
) {
    Row(
        modifier.heightIn(min = AuroraLayout.WorkTitleLine).semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
            if (required) stateDescription = "Обязательный вопрос"
        },
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HbIcon(
            HbIconGlyph.Message, tint = AuroraColors.Amber,
            size = if (compact) AuroraLayout.Glyph else AuroraLayout.RequestGlyph,
        )
        val style = HbTypography.PaneTitle
        BasicText(title, style = style.copy(color = AuroraColors.RequestText))
    }
}

@Composable
internal fun AuroraRequestStop(
    onStop: () -> Unit,
    touch: Boolean,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    AuroraControl(
        onStop,
        modifier,
        label = "Остановить работу",
        touch = touch,
        background = if (floating) AuroraColors.Request else Color.Transparent,
        focus = AuroraColors.Amber,
    ) {
        HbIcon(HbIconGlyph.Close, tint = AuroraColors.RequestSecondary, size = AuroraLayout.Glyph)
    }
}
