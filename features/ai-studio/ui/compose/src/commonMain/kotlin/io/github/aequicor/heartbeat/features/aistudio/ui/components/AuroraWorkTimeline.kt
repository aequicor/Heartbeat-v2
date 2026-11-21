package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyHistoryContent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus

@Composable
internal fun AuroraWorkTimeline(
    reply: Reply,
    expandedSteps: Set<String>,
    expandedEvents: Set<String>,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    onDisclosure: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(if (touch) AuroraLayout.Space else AuroraLayout.ContentInset)) {
        reply.steps.forEachIndexed { index, step ->
            key(step.id) {
                AuroraWorkStage(
                    reply.id, step, "${reply.id}/${step.id}" in expandedSteps, expandedEvents,
                    earlierPhase = index > 0, laterPhase = index < reply.steps.lastIndex, touch, onAction, onDisclosure,
                )
            }
        }
        val interim = reply.interim
        if (interim != null && reply.steps.none { it.result == interim }) {
            AuroraWorkResult(interim, reply.interimContent, touch, Modifier.padding(top = AuroraLayout.Space))
        }
    }
}

@Composable
private fun AuroraCurrentWorkTitle(step: Step, earlierPhase: Boolean) {
    AuroraWorkText(
        step.activeTitle.ifBlank { "Агент работает…" },
        AuroraWorkRole.Detail,
        Modifier.timelineMarker(
            StepStatus.Current, AuroraLayout.WorkTitleLine / 2, before = earlierPhase, after = false,
        ).padding(start = AuroraLayout.WorkTextInset),
        color = if (earlierPhase) AuroraColors.WorkMuted else AuroraColors.WorkInk,
    )
}

@Composable
private fun AuroraWorkStage(
    replyId: String,
    step: Step,
    expanded: Boolean,
    expandedEvents: Set<String>,
    earlierPhase: Boolean,
    laterPhase: Boolean,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    onDisclosure: () -> Unit,
) {
    val current = step.status == StepStatus.Current
    var retainFocusedEvents by remember { mutableStateOf(false) }
    val open = expanded || retainFocusedEvents
    val showEvents = (current || open) && step.events.isNotEmpty()
    Column {
        if (current) {
            AuroraCurrentWorkTitle(step, earlierPhase)
        } else {
            AuroraPastWorkHeader(step, open, earlierPhase, laterPhase || showEvents, touch) {
                onDisclosure()
                retainFocusedEvents = false
                if (expanded || !open) onAction(AiStudioAction.ToggleStep(replyId, step.id))
            }
        }
        if (showEvents) {
            AuroraStageEvents(
                replyId, step, expandedEvents, current, laterPhase, touch,
                onAction = { action ->
                    onDisclosure()
                    onAction(action)
                },
                modifier = Modifier.onFocusChanged {
                    if (current || !it.hasFocus) retainFocusedEvents = it.hasFocus
                }.focusGroup(),
            )
        } else if (expanded && step.details.isNotBlank()) {
            SelectionContainer {
                AuroraWorkText(
                    step.details, AuroraWorkRole.Detail, Modifier.padding(start = AuroraLayout.DesktopTarget),
                )
            }
        }
        if (!current) AuroraStageResult(step.result, step.resultContent, laterPhase, showEvents, touch)
    }
}

@Composable
private fun AuroraStageResult(
    result: String?, content: ReplyHistoryContent?, laterPhase: Boolean, showEvents: Boolean, touch: Boolean,
) {
    if (result != null) {
        val axis = if (laterPhase) Modifier.timelineLine() else Modifier
        AuroraWorkResult(
            result, content, touch,
            axis.fillMaxWidth().padding(
                start = AuroraLayout.DesktopTarget,
                top = if (showEvents) AuroraLayout.ContentInset else Dp.Hairline,
            ),
        )
    }
    if (laterPhase) {
        Spacer(Modifier.fillMaxWidth().height(AuroraLayout.ContentInset).timelineLine())
    }
}

@Composable
private fun AuroraStageEvents(
    replyId: String,
    step: Step,
    expandedEvents: Set<String>,
    current: Boolean,
    laterPhase: Boolean,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = if (touch) AuroraLayout.SmallGap else AuroraLayout.DesktopWorkEventGap
    Column(modifier) {
        step.events.forEachIndexed { index, event ->
            key(event.id) {
                val axis = if (current) Modifier else Modifier.timelineLine()
                Spacer(axis.fillMaxWidth().height(gap))
                val open = "$replyId/${step.id}/${event.id}" in expandedEvents
                val after = laterPhase || index < step.events.lastIndex
                AuroraWorkEvent(event, open, current, connectedBelow = after, touch) {
                    onAction(AiStudioAction.ToggleWorkEvent(replyId, step.id, event.id))
                }
            }
        }
    }
}

@Composable
private fun AuroraPastWorkHeader(
    step: Step, expanded: Boolean, before: Boolean, after: Boolean, touch: Boolean, onClick: () -> Unit,
) {
    val target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopWorkDisclosureTarget
    val expandable = step.events.isNotEmpty() || step.details.isNotBlank()
    val markerY = if (touch) target / 2 else null
    val labelInset = if (expandable && !touch) AuroraLayout.SmallGap else Dp.Hairline
    val marker = Modifier.fillMaxWidth().timelineMarker(step.status, markerY, before, after)
        .padding(start = AuroraLayout.DesktopTarget - labelInset)
    val role = AuroraWorkRole.Action
    val ink = if (touch) AuroraColors.WorkMuted else AuroraColors.DesktopInk
    val inset = if (touch) AuroraLayout.ControlGap else AuroraLayout.SmallGap
    if (!expandable) {
        AuroraWorkText(step.title, role, marker.padding(vertical = inset), color = ink)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    AuroraControl(
        onClick,
        marker.hoverable(interaction, enabled = !touch)
            .semantics { stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто" },
        touch = touch, background = if (hovered) AuroraColors.WorkHover else Color.Transparent,
        round = false, horizontalPadding = Dp.Hairline,
        minimumTarget = target,
    ) {
        val label = Modifier.weight(1f, fill = false)
        AuroraWorkText(
            step.title, role, if (touch) label else label.padding(start = labelInset, top = inset, bottom = inset),
            color = ink,
        )
        AuroraWorkChevron(
            expanded, Modifier.padding(start = AuroraLayout.Space),
            tint = if (touch) AuroraColors.Muted else AuroraColors.DesktopInk,
        )
    }
}

@Composable
internal fun AuroraWorkChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = AuroraColors.Muted,
    expansion: (() -> Float)? = null,
) {
    val rotation = if (expansion == null) {
        modifier.rotate(if (expanded) 0f else ChevronRightRotation)
    } else modifier.graphicsLayer { rotationZ = ChevronRightRotation * (1f - expansion()) }
    HbIcon(HbIconGlyph.ChevronDown, rotation, tint = tint, size = AuroraLayout.Glyph)
}

internal fun Modifier.timelineMarker(
    status: StepStatus,
    y: Dp?,
    before: Boolean,
    after: Boolean,
    event: Boolean = false,
    offsetY: () -> Float = { 0f },
): Modifier = drawBehind {
    val point = Offset(AuroraLayout.WorkAxis.toPx(), (y?.toPx() ?: size.height / 2f) + offsetY())
    val radius = if (status == StepStatus.Current && !event) {
        AuroraLayout.ActiveMarker.toPx() / 2
    } else {
        AuroraLayout.NavigationStatus.toPx() / 2
    }
    val lineWidth = AuroraLayout.Stroke.toPx()
    val color = status.markerColor(event)
    if (before) drawLine(AuroraColors.StageLine, Offset(point.x, 0f), point, lineWidth)
    if (after) drawLine(AuroraColors.StageLine, point, Offset(point.x, size.height), lineWidth)
    if (status == StepStatus.Done && !event) {
        drawCircle(color, radius = radius, center = point)
    } else {
        drawCircle(AuroraColors.Pearl, radius = radius, center = point)
        val stroke = if (event) AuroraLayout.WorkEventStroke.toPx() else lineWidth * 2
        drawCircle(color, radius = radius, center = point, style = Stroke(stroke))
    }
}

private fun StepStatus.markerColor(event: Boolean): Color = when (this) {
    StepStatus.Current -> if (event) AuroraColors.WorkRunning else AuroraColors.Primary
    StepStatus.Done -> if (event) AuroraColors.Green else AuroraColors.StageComplete
    StepStatus.Stopped -> AuroraColors.WorkRunning
    StepStatus.Failed -> AuroraColors.Error
}

private const val ChevronRightRotation = -90f

private fun Modifier.timelineLine(): Modifier = drawBehind {
    val x = AuroraLayout.WorkAxis.toPx()
    drawLine(AuroraColors.StageLine, Offset(x, 0f), Offset(x, size.height), AuroraLayout.Stroke.toPx())
}
