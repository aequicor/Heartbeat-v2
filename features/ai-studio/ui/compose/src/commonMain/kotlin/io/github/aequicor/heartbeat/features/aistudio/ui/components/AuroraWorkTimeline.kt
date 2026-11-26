package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyHistoryContent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind

@Composable
internal fun AuroraWorkTimeline(
    reply: Reply,
    expandedSteps: Set<String>,
    expandedEvents: Set<String>,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    onDisclosure: () -> Unit = {},
    workMenu: AuroraWorkMenu? = null,
) {
    val active = reply.status == ReplyStatus.Working
    val inset = AuroraLayout.Space
    val axis = if (touch) AuroraLayout.WorkAxis else AuroraLayout.DesktopWorkAxis - inset
    val interruptedSingleEvent = !touch && (reply.status == ReplyStatus.Failed || reply.status == ReplyStatus.Stopped) &&
        reply.steps.singleOrNull()?.events?.size == 1
    val verticalInset = if (touch || active) inset else AuroraLayout.ContentInset
    val interim = reply.interim?.takeUnless { text -> reply.steps.any { it.result == text } }
    val preparation = interim?.takeUnless { touch }
    val entrance = remember(reply.id) { ShownWork().apply { record(reply) } }.takeIf { active }
    if (entrance != null) SideEffect { entrance.record(reply) }
    if (reply.steps.isEmpty() && interim == null) return
    val region = if (!active && workMenu != null) {
        workMenu.region(AiStudioAction.ToggleWork(reply.id), "Свернуть ход работы")
    } else Modifier
    Column(modifier.then(region).fillMaxWidth().padding(horizontal = inset, vertical = verticalInset)) {
        if (preparation != null) {
            val lineHeight = HbTypography.Body.lineHeight
            val firstLineCenter = with(LocalDensity.current) { lineHeight.toDp() / 2 }
            AuroraWorkResult(
                preparation, reply.interimContent, touch,
                Modifier.fillMaxWidth().timelineMarker(
                    StepStatus.Done, axis, firstLineCenter, before = false, after = reply.steps.isNotEmpty(), touch = false,
                ).padding(start = workContentInset(touch), bottom = AuroraLayout.ContentInset),
                contentModifier = Modifier.auroraFadeInOnEntry(entrance?.interim == false),
            )
        }
        reply.steps.forEachIndexed { index, step ->
            key(step.id) {
                AuroraWorkStage(
                    reply.id, step, "${reply.id}/${step.id}" in expandedSteps, expandedEvents,
                    earlierPhase = preparation != null || index > 0, laterPhase = index < reply.steps.lastIndex,
                    axis, interruptedSingleEvent, touch, onAction, onDisclosure, workMenu, entrance,
                )
            }
        }
        if (touch && interim != null) {
            AuroraWorkResult(
                interim, reply.interimContent, touch, Modifier.padding(top = AuroraLayout.Space),
                contentModifier = Modifier.auroraFadeInOnEntry(entrance?.interim == false),
            )
        }
    }
}

private class ShownWork {
    val events = mutableSetOf<String>()
    val results = mutableSetOf<String>()
    var interim = false
        private set
    private var steps: List<Step>? = null

    fun record(reply: Reply) {
        interim = interim || reply.interim != null
        if (steps === reply.steps) return
        steps = reply.steps
        for (step in reply.steps) {
            if (step.result != null) results.add(step.id)
            step.events.forEach { events.add(it.id) }
        }
    }
}

@Composable
internal fun Modifier.auroraFadeInOnEntry(animateOnEntry: Boolean): Modifier {
    val animate = remember { animateOnEntry } && HbTheme.motion != HbMotionPreference.Reduced
    if (!animate) return this
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(opacity) {
        opacity.animateTo(1f, tween(HbMotions.Default.smallStateChangeMs, easing = LinearOutSlowInEasing))
    }
    return graphicsLayer { alpha = opacity.value }
}

@Composable
private fun AuroraCurrentWorkTitle(step: Step, earlierPhase: Boolean, laterMarker: Boolean, axis: Dp, touch: Boolean) {
    val title = step.activeTitle.ifBlank { "Агент работает" }
    val ink = if (earlierPhase && (touch || step.activeTitle.isNotBlank())) AuroraColors.WorkMuted else AuroraColors.WorkInk
    if (touch) {
        AuroraWorkText(
            step.activeTitle.ifBlank { "Агент работает…" }, AuroraWorkRole.Detail,
            Modifier.timelineMarker(
                StepStatus.Current, axis, AuroraLayout.WorkTitleLine / 2, earlierPhase, after = false, touch = true,
            ).padding(start = AuroraLayout.WorkTextInset),
            color = ink,
        )
    } else {
        AuroraDesktopWorkGoal(title, before = earlierPhase, after = laterMarker, ink = ink)
    }
}

@Composable
internal fun AuroraDesktopWorkGoal(
    text: String,
    modifier: Modifier = Modifier,
    before: Boolean = false,
    after: Boolean = false,
    ink: Color = AuroraColors.WorkInk,
) {
    val axis = AuroraLayout.DesktopWorkAxis - AuroraLayout.Space
    var firstLineCenter by remember { mutableFloatStateOf(0f) }
    Row(
        modifier.timelineMarker(StepStatus.Current, axis, Dp.Hairline, before, after, touch = false,
            offsetY = { firstLineCenter },
        ).padding(start = axis + AuroraLayout.ActiveMarker / 2 + AuroraLayout.DesktopWorkGoalGap),
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.WorkActivityDotsGap),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(
            text, Modifier.weight(1f, fill = false),
            style = HbTypography.Detail.copy(color = ink),
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                firstLineCenter = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
            },
        )
        AuroraWorkActivityDots(Modifier.width(AuroraLayout.WorkActivityDotsWidth)
            .padding(top = AuroraLayout.DesktopWorkActivityDotsTopInset))
    }
}

@Composable
private fun AuroraWorkStage(
    replyId: String,
    step: Step,
    expanded: Boolean,
    expandedEvents: Set<String>,
    earlierPhase: Boolean,
    laterPhase: Boolean,
    axis: Dp,
    interruptedSingleEvent: Boolean,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    onDisclosure: () -> Unit,
    workMenu: AuroraWorkMenu?,
    entrance: ShownWork?,
) {
    val current = step.status == StepStatus.Current
    val standalone = !touch && !current && step.events.size == 1
    var retainFocusedEvents by remember { mutableStateOf(false) }
    var previousStandaloneEvent by remember { mutableStateOf<String?>(null) }
    val stageFocus = remember { FocusRequester() }
    val hasInterim = step.result != null
    LaunchedEffect(hasInterim) {
        if (hasInterim && !standalone && retainFocusedEvents) {
            stageFocus.requestFocus()
            retainFocusedEvents = false
        }
    }
    if (standalone) {
        // Keep the event's open details if a later tool adds a group header above it.
        val eventKey = "$replyId/${step.id}/${step.events.single().id}"
        SideEffect { previousStandaloneEvent = eventKey }
    }
    val open = expanded || retainFocusedEvents || previousStandaloneEvent?.let(expandedEvents::contains) == true
    val showEvents = (current || open || standalone) && step.events.isNotEmpty()
    val region = if (open && !current && !standalone && workMenu != null) {
        workMenu.region(AiStudioAction.SetWorkExpanded(replyId, false, step.id), "Свернуть этап")
    } else Modifier
    Column(region) {
        if (current) {
            AuroraCurrentWorkTitle(step, earlierPhase, showEvents || laterPhase, axis, touch)
        } else if (!standalone) {
            AuroraPastWorkHeader(step, open, earlierPhase, laterPhase || showEvents, axis, touch, stageFocus) {
                onDisclosure()
                retainFocusedEvents = false
                previousStandaloneEvent = null
                if (expanded || !open) onAction(AiStudioAction.ToggleStep(replyId, step.id))
            }
        }
        if (showEvents) {
            AuroraStageEvents(
                replyId, step, expandedEvents, earlierPhase, laterPhase, standalone, axis, interruptedSingleEvent, touch,
                onAction = onAction,
                modifier = Modifier.onFocusChanged {
                    if (current || standalone || !it.hasFocus) retainFocusedEvents = it.hasFocus
                }.focusGroup(),
                workMenu = workMenu,
                entrance = entrance,
            )
        } else if (expanded && step.details.isNotBlank()) {
            val connector = if (laterPhase && !touch) Modifier.timelineLine(axis) else Modifier
            SelectionContainer(modifier = connector) {
                AuroraWorkText(
                    step.details, AuroraWorkRole.Detail, Modifier.padding(start = workContentInset(touch)),
                )
            }
        }
        if (!current) AuroraStageResult(
            step.result, step.resultContent, laterPhase, showEvents, axis, touch,
            animateOnEntry = entrance?.results?.contains(step.id) == false,
        )
    }
}

@Composable
private fun AuroraStageResult(
    result: String?, content: ReplyHistoryContent?, laterPhase: Boolean, showEvents: Boolean, axis: Dp, touch: Boolean,
    animateOnEntry: Boolean,
) {
    if (result != null) {
        val connector = if (laterPhase) Modifier.timelineLine(axis) else Modifier
        AuroraWorkResult(
            result, content, touch,
            connector.fillMaxWidth().padding(
                start = workContentInset(touch),
                top = if (showEvents) AuroraLayout.ContentInset else Dp.Hairline,
            ),
            contentModifier = Modifier.auroraFadeInOnEntry(animateOnEntry),
        )
    }
    if (laterPhase) {
        Spacer(Modifier.fillMaxWidth().height(AuroraLayout.ContentInset).timelineLine(axis))
    }
}

@Composable
private fun AuroraStageEvents(
    replyId: String,
    step: Step,
    expandedEvents: Set<String>,
    earlierPhase: Boolean,
    laterPhase: Boolean,
    standalone: Boolean,
    axis: Dp,
    interruptedSingleEvent: Boolean,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    workMenu: AuroraWorkMenu? = null,
    entrance: ShownWork? = null,
) {
    val current = step.status == StepStatus.Current
    val gap = if (touch) AuroraLayout.SmallGap else AuroraLayout.DesktopWorkEventGap
    Column(modifier) {
        step.events.forEachIndexed { index, event ->
            key(event.id) {
                if (!standalone) {
                    val connector = if (current && touch) Modifier else Modifier.timelineLine(axis)
                    Spacer(connector.fillMaxWidth().height(gap))
                }
                val open = "$replyId/${step.id}/${event.id}" in expandedEvents
                val after = laterPhase || index < step.events.lastIndex
                val region = if (open && workMenu != null) {
                    val label = if (event.kind == WorkEventKind.Thinking) "Свернуть размышления" else "Свернуть инструмент"
                    workMenu.region(AiStudioAction.SetWorkExpanded(replyId, false, step.id, event.id), label)
                } else Modifier
                AuroraWorkEvent(
                    event, open, current, connectedBelow = after, touch, axis,
                    standalone = standalone, connectedAbove = !standalone || earlierPhase,
                    markerDiameter = if (interruptedSingleEvent) AuroraLayout.ActiveMarker else AuroraLayout.NavigationStatus,
                    modifier = region,
                    animateOnEntry = entrance?.events?.contains(event.id) == false,
                    fileContent = {
                        AuroraArtifactRows(
                            event.files, replyId, touch, onAction,
                            Modifier.padding(top = AuroraLayout.SmallGap, end = AuroraLayout.Space),
                        )
                    },
                ) {
                    onAction(AiStudioAction.ToggleWorkEvent(replyId, step.id, event.id))
                }
            }
        }
    }
}

@Composable
private fun AuroraPastWorkHeader(
    step: Step, expanded: Boolean, before: Boolean, after: Boolean, axis: Dp, touch: Boolean,
    focus: FocusRequester, onClick: () -> Unit,
) {
    val target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopWorkDisclosureTarget
    val expandable = step.events.isNotEmpty() || step.details.isNotBlank()
    val markerY = if (touch) target / 2 else null
    val labelInset = if (expandable && !touch) AuroraLayout.SmallGap else Dp.Hairline
    val marker = Modifier.fillMaxWidth().timelineMarker(step.status, axis, markerY, before, after, touch)
        .padding(start = workContentInset(touch) - labelInset)
    val role = if (touch) AuroraWorkRole.Action else AuroraWorkRole.StageTitle
    val ink = if (touch || step.result != null) AuroraColors.WorkMuted else AuroraColors.DesktopInk
    val inset = if (touch) AuroraLayout.ControlGap else AuroraLayout.SmallGap
    if (!expandable) {
        AuroraWorkText(step.title, role, marker.padding(vertical = inset), color = ink)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val showChevron = hovered || focused && HbFocusIndication.keyboardNavigation
    val calls = step.events.size
    val running = if (step.result == null) 0 else step.events.count { it.status == StepStatus.Current }
    AuroraControl(
        onClick,
        marker.focusRequester(focus).onFocusChanged { focused = it.isFocused }.hoverable(interaction, enabled = !touch)
            .semantics { stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто" },
        touch = touch, background = Color.Transparent,
        round = false, horizontalPadding = Dp.Hairline,
        minimumTarget = target,
    ) {
        if (touch) {
            AuroraWorkText(step.title, role, Modifier.weight(1f, fill = false), color = ink)
            AuroraWorkChevron(expanded, Modifier.padding(start = AuroraLayout.Space))
        } else {
            Row(
                Modifier.weight(1f).padding(start = labelInset, end = AuroraLayout.Space).padding(vertical = inset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuroraWorkText(
                    step.title, role, Modifier.weight(1f, fill = false), color = ink,
                )
                AuroraWorkChevron(
                    expanded,
                    Modifier.padding(start = AuroraLayout.Space)
                        .offset(y = AuroraLayout.DesktopWorkStageChevronOffset * HbTheme.textScale)
                        .graphicsLayer { alpha = if (showChevron) 1f else 0f },
                    tint = AuroraColors.WorkMuted,
                )
            }
            if (calls > 0) {
                val count = if (running > 0) "$running в работе" else auroraWorkCount(calls, "вызов", "вызова", "вызовов")
                AuroraWorkText(
                    count, AuroraWorkRole.Metadata, Modifier.padding(end = AuroraLayout.DesktopWorkCountEndInset),
                    color = AuroraColors.WorkMuted, maxLines = 1,
                )
            }
        }
    }
}

private fun workContentInset(touch: Boolean) =
    if (touch) AuroraLayout.DesktopTarget else AuroraLayout.DesktopWorkContentInset

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
    axis: Dp,
    y: Dp?,
    before: Boolean,
    after: Boolean,
    touch: Boolean,
    event: Boolean = false,
    diameter: Dp = if (status == StepStatus.Current && !event) AuroraLayout.ActiveMarker else AuroraLayout.NavigationStatus,
    filled: Boolean = !event && status == StepStatus.Done,
    offsetY: () -> Float = { 0f },
): Modifier = drawBehind {
    val point = Offset(axis.toPx(), (y?.toPx() ?: size.height / 2f) + offsetY())
    val radius = diameter.toPx() / 2
    val lineWidth = AuroraLayout.Stroke.toPx()
    val color = if (!touch && filled && status == StepStatus.Done) {
        AuroraColors.StageComplete
    } else status.markerColor(event)
    if (before) drawLine(AuroraColors.StageLine, Offset(point.x, 0f), point, lineWidth)
    if (after) drawLine(AuroraColors.StageLine, point, Offset(point.x, size.height), lineWidth)
    if (filled) {
        drawCircle(color, radius = radius, center = point)
    } else {
        drawCircle(AuroraColors.Pearl, radius = radius, center = point)
        val stroke = if (event) AuroraLayout.WorkEventStroke.toPx() else lineWidth * 2
        val strokeRadius = if (touch) radius else radius - stroke / 2
        drawCircle(color, radius = strokeRadius, center = point, style = Stroke(stroke))
    }
}

private fun StepStatus.markerColor(event: Boolean): Color = when (this) {
    StepStatus.Current -> if (event) AuroraColors.WorkRunning else AuroraColors.Primary
    StepStatus.Done -> if (event) AuroraColors.Green else AuroraColors.StageComplete
    StepStatus.Stopped -> AuroraColors.WorkRunning
    StepStatus.Failed -> AuroraColors.Error
}

private const val ChevronRightRotation = -90f

private fun Modifier.timelineLine(axis: Dp): Modifier = drawBehind {
    val x = axis.toPx()
    drawLine(AuroraColors.StageLine, Offset(x, 0f), Offset(x, size.height), AuroraLayout.Stroke.toPx())
}
