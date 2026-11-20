package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind

@Composable
internal fun AuroraWorkEvent(
    event: WorkEvent,
    expanded: Boolean,
    current: Boolean,
    connectedBelow: Boolean,
    touch: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val thinking = event.kind == WorkEventKind.Thinking
    val transition = rememberThinkingHeaderTransition(thinking, expanded)
    val titledPreview = !expanded && thinking && !event.title.isNullOrBlank()
    val target = if (touch || titledPreview) {
        AuroraLayout.WorkEventTarget
    } else {
        AuroraLayout.DesktopTarget
    }
    var headerHeight by remember(target) { mutableStateOf(target) }
    val axis = if (current) Modifier else Modifier.timelineMarker(
        event.status, headerHeight / 2, before = true, after = connectedBelow, event = true,
        offsetY = { transition?.heightOffset(expanded)?.div(2) ?: 0f },
    ).padding(start = AuroraLayout.DesktopTarget)
    Column(
        axis.fillMaxWidth().clip(RoundedCornerShape(AuroraLayout.SmallRadius))
            .workEventBackground(current, expanded, transition?.progress),
    ) {
        AuroraWorkEventHeader(
            event, expanded, current, touch, transition, onClick,
            Modifier.zIndex(1f).heightIn(min = target).onSizeChanged {
                headerHeight = with(density) { it.height.toDp() }
                transition?.recordHeight(expanded, it.height)
            },
        )
        if (expanded) {
            // The work timeline already animates height and keeps the active row anchored during reflow.
            val fade = if (transition == null) Modifier else Modifier.graphicsLayer {
                alpha = transition.detailsOpacity()
                translationY = transition.heightOffset(expanded = true)
            }
            AuroraWorkDetailsBody(event, touch, fade)
        }
    }
}

@Composable
private fun rememberThinkingHeaderTransition(thinking: Boolean, expanded: Boolean): ThinkingHeaderTransition? {
    if (!thinking || HbTheme.motion == HbMotionPreference.Reduced) return null
    val progress = animateFloatAsState(
        if (expanded) 1f else 0f, tween(HbMotions.Default.ordinaryTransitionMs), label = "Thinking details",
    )
    return remember(progress) { ThinkingHeaderTransition { progress.value } }
}

private class ThinkingHeaderTransition(val progress: () -> Float) {
    private var collapsedHeight by mutableIntStateOf(0)
    private var expandedHeight by mutableIntStateOf(0)
    var previewHeight by mutableIntStateOf(0)
    val drawPreview by derivedStateOf { previewOpacity() > 0f }

    fun previewOpacity(): Float = (1f - progress() * 2f).coerceAtLeast(0f)

    fun detailsOpacity(): Float = (progress() * 2f - 1f).coerceAtLeast(0f)

    fun recordHeight(expanded: Boolean, height: Int) {
        if (expanded) expandedHeight = height else collapsedHeight = height
    }

    fun heightOffset(expanded: Boolean): Float = heightGap() * remaining(expanded)

    fun labelOffset(expanded: Boolean): Float = (heightGap() - previewHeight) * remaining(expanded) / 2f

    private fun heightGap(): Int = if (collapsedHeight == 0 || expandedHeight == 0) 0
    else collapsedHeight - expandedHeight

    private fun remaining(expanded: Boolean): Float = if (expanded) 1f - progress() else -progress()
}

private fun Modifier.workEventBackground(current: Boolean, expanded: Boolean, expansion: (() -> Float)?): Modifier =
    when {
        current -> background(AuroraColors.Current)
        expansion != null -> drawBehind { drawRect(AuroraColors.WorkHistory.copy(alpha = expansion())) }
        expanded -> background(AuroraColors.WorkHistory)
        else -> this
    }

@Composable
private fun AuroraWorkEventHeader(
    event: WorkEvent,
    expanded: Boolean,
    current: Boolean,
    touch: Boolean,
    transition: ThinkingHeaderTransition?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val thinking = event.kind == WorkEventKind.Thinking
    val compactTool = !touch && !thinking
    val tint = when {
        !touch -> AuroraColors.DesktopInk
        current -> AuroraColors.Ink
        else -> AuroraColors.Primary
    }
    val inset = if (!touch && thinking) AuroraLayout.SmallGap else AuroraLayout.ControlGap
    val movingIcon = thinkingIconMotion(transition, expanded)
    val hoverColor = if (hovered) AuroraColors.WorkHover else Color.Transparent
    AuroraControl(
        onClick,
        modifier.fillMaxWidth().then(thinkingHeaderSurface(transition, expanded, hoverColor))
            .hoverable(interaction).semantics {
                val disclosure = if (expanded) "Развёрнуто" else "Свёрнуто"
                stateDescription = if (compactTool) disclosure else "${event.status.label} · $disclosure"
            },
        touch = touch, background = if (transition == null) hoverColor else Color.Transparent, round = false,
        clipContent = transition == null,
    ) {
        Box(movingIcon.width(AuroraLayout.FileTile - AuroraLayout.Glyph), contentAlignment = Alignment.Center) {
            HbIcon(event.kind.glyph(), tint = tint, size = AuroraLayout.Glyph)
        }
        val text = Modifier.weight(1f).padding(horizontal = AuroraLayout.Space, vertical = inset)
        if (compactTool) {
            val name = event.title.orEmpty().ifBlank { event.summary.takeUnless { it == event.status.label }.orEmpty() }
            AuroraToolLabels(name, event.status, expanded, text)
        } else {
            AuroraWorkStackedLabels(event, touch, showPreview = !thinking || !expanded, transition, text)
        }
        AuroraWorkChevron(
            expanded, movingIcon, tint = if (touch) AuroraColors.Muted else AuroraColors.DesktopInk,
            expansion = transition?.progress,
        )
    }
}

private fun thinkingIconMotion(transition: ThinkingHeaderTransition?, expanded: Boolean): Modifier =
    if (transition == null) Modifier else Modifier.graphicsLayer {
        translationY = transition.heightOffset(expanded) / 2f
    }

private fun thinkingHeaderSurface(transition: ThinkingHeaderTransition?, expanded: Boolean, hover: Color): Modifier =
    if (transition == null) Modifier else Modifier.drawWithContent {
        val shownHeight = size.height + transition.heightOffset(expanded)
        drawRoundRect(
            hover, size = Size(size.width, shownHeight), cornerRadius = CornerRadius(AuroraLayout.SmallRadius.toPx()),
        )
        clipRect(bottom = shownHeight) { this@drawWithContent.drawContent() }
    }

@Composable
private fun AuroraWorkStackedLabels(
    event: WorkEvent, touch: Boolean, showPreview: Boolean, transition: ThinkingHeaderTransition?,
    modifier: Modifier = Modifier,
) {
    val title = event.title.orEmpty().ifBlank { if (showPreview) "" else "Размышления" }
    val shift = if (transition == null || event.title.isNullOrBlank()) Modifier else Modifier.graphicsLayer {
        translationY = transition.labelOffset(expanded = !showPreview)
    }
    Column(modifier.then(shift)) {
        if (title.isNotBlank()) {
            AuroraWorkText(
                title, AuroraWorkRole.Action,
                color = if (touch) AuroraColors.WorkInk else AuroraColors.DesktopInk,
            )
        }
        if (showPreview || transition?.drawPreview == true) {
            val previewLines = if (event.kind == WorkEventKind.Thinking) 2 else Int.MAX_VALUE
            AuroraWorkText(
                event.preview, AuroraWorkRole.Summary,
                modifier = Modifier.thinkingPreviewTransition(showPreview, transition),
                color = if (touch) AuroraColors.WorkMuted else AuroraColors.DesktopWorkMuted,
                maxLines = previewLines,
            )
        }
    }
}

private fun Modifier.thinkingPreviewTransition(showPreview: Boolean, transition: ThinkingHeaderTransition?): Modifier {
    if (transition == null) return this
    val semantics = if (showPreview) Modifier else Modifier.semantics { hideFromAccessibility() }
    // Draw the leaving preview at its old size without adding it to the timeline's target height.
    return layout { measurable, constraints ->
        val preview = measurable.measure(constraints)
        transition.previewHeight = preview.height
        layout(preview.width, if (showPreview) preview.height else 0) { preview.placeRelative(0, 0) }
    }.graphicsLayer { alpha = transition.previewOpacity() }.then(semantics)
}

@Composable
private fun AuroraToolLabels(title: String, status: StepStatus, expanded: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AuroraWorkText(
            title, AuroraWorkRole.Action, Modifier.weight(1f).padding(end = AuroraLayout.ContentInset),
            color = AuroraColors.DesktopInk, maxLines = if (expanded) Int.MAX_VALUE else 1,
        )
        AuroraWorkText(
            status.label, AuroraWorkRole.Metadata,
            color = when (status) {
                StepStatus.Done -> AuroraColors.Green
                StepStatus.Failed -> AuroraColors.Error
                else -> AuroraColors.DesktopWorkMuted
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun AuroraWorkDetailsBody(event: WorkEvent, touch: Boolean, modifier: Modifier = Modifier) {
    val verticalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.Space
    SelectionContainer(
        modifier.fillMaxWidth().drawBehind {
            val stroke = AuroraLayout.Stroke.toPx()
            val y = stroke / 2
            drawLine(AuroraColors.WorkDivider, Offset(0f, y), Offset(size.width, y), stroke)
        }.padding(
            start = AuroraLayout.WorkTextInset, end = AuroraLayout.ContentInset,
            top = verticalInset, bottom = verticalInset,
        ),
    ) {
        Column {
            val label = event.detailLabel
            if (label != null) {
                AuroraWorkText(
                    label, AuroraWorkRole.Action,
                    Modifier.padding(bottom = AuroraLayout.Space),
                    color = if (touch) AuroraColors.WorkMuted else AuroraColors.DesktopInk,
                )
            }
            AuroraWorkText(
                event.detailText, AuroraWorkRole.Detail, WorkDetailWidth,
                color = if (touch) AuroraColors.WorkInk else AuroraColors.DesktopInk,
            )
        }
    }
}

// A paragraph wraps within the reply; its unwrapped length must not set the card width.
private object WorkDetailWidth : LayoutModifier {
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(height)

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints) =
        measurable.measure(constraints).let { detail ->
            layout(detail.width, detail.height) { detail.placeRelative(0, 0) }
        }
}

private fun WorkEventKind.glyph() = when (this) {
    WorkEventKind.File -> HbIconGlyph.Document
    WorkEventKind.Search, WorkEventKind.Check -> HbIconGlyph.Search
    WorkEventKind.Thinking -> HbIconGlyph.Thinking
}
