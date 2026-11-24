package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ToolOperation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.commandCodeDetails
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.diffCodeDetails
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.readCodeDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun AuroraWorkEvent(
    event: WorkEvent,
    expanded: Boolean,
    current: Boolean,
    connectedBelow: Boolean,
    touch: Boolean,
    axis: Dp = AuroraLayout.WorkAxis,
    standalone: Boolean = false,
    connectedAbove: Boolean = true,
    markerDiameter: Dp = AuroraLayout.NavigationStatus,
    modifier: Modifier = Modifier,
    animateOnEntry: Boolean = false,
    fileContent: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val thinking = event.kind == WorkEventKind.Thinking
    val transition = rememberThinkingHeaderTransition(thinking, expanded)
    val motion = transition?.takeIf { it.isActive(expanded) }
    val titledPreview = !expanded && thinking && !event.title.isNullOrBlank()
    val target = if (touch || titledPreview) {
        AuroraLayout.WorkEventTarget
    } else {
        AuroraLayout.DesktopTarget
    }
    var headerHeight by remember { mutableIntStateOf(with(density) { target.roundToPx() }) }
    val marker = if (current && touch) Modifier else Modifier.timelineMarker(
        event.status, axis, Dp.Hairline, before = connectedAbove, after = connectedBelow, touch, event = true,
        diameter = markerDiameter,
        filled = standalone,
        offsetY = { (headerHeight + (motion?.heightOffset(expanded) ?: 0f)) / 2f },
    ).padding(start = if (touch) AuroraLayout.DesktopTarget else AuroraLayout.DesktopWorkContentInset)
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    val background = when {
        !touch || thinking -> Modifier
        current -> Modifier.background(AuroraColors.Current)
        expanded -> Modifier.background(AuroraColors.WorkHistory)
        else -> Modifier
    }
    var wasExpanded by remember { mutableStateOf(expanded) }
    SideEffect { wasExpanded = expanded }
    Column(
        modifier.then(marker).fillMaxWidth().clip(shape).then(background),
    ) {
        AuroraWorkEventHeader(
            event, expanded, current, touch, motion, onClick,
            Modifier.auroraFadeInOnEntry(animateOnEntry).zIndex(1f).heightIn(min = target).onSizeChanged {
                headerHeight = it.height
                transition?.recordHeight(expanded, it.height)
            },
        )
        if (expanded) {
            // The work timeline already animates height and keeps the active row anchored during reflow.
            val fade = if (motion == null) Modifier.auroraFadeInOnEntry(!wasExpanded) else Modifier.graphicsLayer {
                alpha = motion.detailsOpacity()
                translationY = motion.heightOffset(expanded = true)
            }
            val panel = if (touch) fade else fade.padding(end = AuroraLayout.Space).clip(shape)
                .background(AuroraColors.WorkAccess)
            AuroraWorkDetailsBody(event, touch, panel, fileContent)
        }
    }
}

@Composable
private fun rememberThinkingHeaderTransition(thinking: Boolean, expanded: Boolean): ThinkingHeaderTransition? {
    if (!thinking || HbTheme.motion == HbMotionPreference.Reduced) return null
    val target = if (expanded) 1f else 0f
    // Already settled rows need no animation coroutine or layers until the user toggles disclosure.
    val transition = remember { ThinkingHeaderTransition(target) }
    if (transition.isActive(expanded)) {
        val animation = remember { Animatable(transition.initialProgress) }
        SideEffect { transition.begin(animation.asState()) }
        LaunchedEffect(target) { animation.animateTo(target, tween(HbMotions.Default.ordinaryTransitionMs)) }
    }
    return transition
}

private class ThinkingHeaderTransition(val initialProgress: Float) {
    private var animation by mutableStateOf<State<Float>?>(null)
    val progress: () -> Float = { animation?.value ?: initialProgress }

    fun isActive(expanded: Boolean): Boolean = animation != null || expanded != (initialProgress == 1f)

    fun begin(progress: State<Float>) {
        animation = progress
    }

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
    var focused by remember { mutableStateOf(false) }
    val showControls = touch || hovered || focused && HbFocusIndication.keyboardNavigation
    val thinking = event.kind == WorkEventKind.Thinking
    val compactTool = !touch && !thinking
    val tint = when {
        !touch -> AuroraColors.DesktopInk
        current -> AuroraColors.Ink
        else -> AuroraColors.Primary
    }
    val inset = if (!touch && thinking) AuroraLayout.SmallGap else AuroraLayout.ControlGap
    val movingIcon = thinkingIconMotion(transition, expanded)
    val hoverColor = if (touch && hovered && !thinking) AuroraColors.WorkHover else Color.Transparent
    AuroraControl(
        onClick,
        modifier.fillMaxWidth().then(thinkingHeaderClip(transition, expanded))
            .onFocusChanged { focused = it.isFocused }.hoverable(interaction).semantics {
                val disclosure = if (expanded) "Развёрнуто" else "Свёрнуто"
                stateDescription = if (compactTool) disclosure else "${event.status.label} · $disclosure"
            },
        touch = touch, background = hoverColor, round = false,
        horizontalPadding = if (touch) AuroraLayout.Space else Dp.Hairline,
        clipContent = transition == null,
    ) {
        val glyph = event.glyph()
        if (touch) {
            Box(movingIcon.width(AuroraLayout.FileTile - AuroraLayout.Glyph), contentAlignment = Alignment.Center) {
                HbIcon(glyph, tint = tint, size = AuroraLayout.Glyph)
            }
        } else {
            HbIcon(glyph, movingIcon.workIconOutline(glyph), tint = tint, size = AuroraLayout.Glyph)
        }
        val text = Modifier.weight(1f).padding(horizontal = AuroraLayout.Space, vertical = inset)
        if (compactTool) {
            AuroraToolLabels(event, expanded, showStatus = showControls, modifier = text)
        } else {
            val showPreview = !thinking || !expanded
            AuroraWorkStackedLabels(event, touch, showPreview, transition, text)
        }
        AuroraWorkChevron(
            expanded, if (touch) movingIcon else movingIcon.padding(end = AuroraLayout.Space)
                .graphicsLayer { alpha = if (showControls) 1f else 0f },
            tint = if (touch) AuroraColors.Muted else AuroraColors.DesktopInk,
            expansion = transition?.progress,
        )
    }
}

private fun thinkingIconMotion(transition: ThinkingHeaderTransition?, expanded: Boolean): Modifier =
    if (transition == null) Modifier else Modifier.graphicsLayer {
        translationY = transition.heightOffset(expanded) / 2f
    }

private fun thinkingHeaderClip(transition: ThinkingHeaderTransition?, expanded: Boolean): Modifier =
    if (transition == null) Modifier else Modifier.drawWithContent {
        val shownHeight = size.height + transition.heightOffset(expanded)
        clipRect(bottom = shownHeight) { this@drawWithContent.drawContent() }
    }

@Composable
private fun AuroraWorkStackedLabels(
    event: WorkEvent, touch: Boolean, showPreview: Boolean, transition: ThinkingHeaderTransition?,
    modifier: Modifier = Modifier,
) {
    val title = event.visibleTitle.orEmpty().ifBlank { if (showPreview) "" else "Размышления" }
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
            val preview = Modifier.thinkingPreviewTransition(showPreview, transition)
            val color = if (touch) AuroraColors.WorkMuted else AuroraColors.DesktopWorkMuted
            if (event.kind == WorkEventKind.Thinking) {
                AuroraThinkingPreview(event, color, preview)
            } else {
                val text = event.commandPreview ?: event.preview.takeUnless { event.isCommand && it == title }
                if (text != null) AuroraWorkText(
                    text, AuroraWorkRole.Summary, preview, color,
                    maxLines = if (event.command != null) 2 else Int.MAX_VALUE,
                )
            }
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
private fun AuroraToolLabels(event: WorkEvent, expanded: Boolean, showStatus: Boolean, modifier: Modifier = Modifier) {
    val title = event.commandPreview ?: event.title.orEmpty().ifBlank {
        event.summary.takeUnless { it == event.status.label }.orEmpty()
    }
    val status = event.status
    val separatorGap = AuroraLayout.DesktopWorkSeparatorGap * HbTheme.textScale
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).padding(end = AuroraLayout.Space), verticalAlignment = Alignment.CenterVertically) {
            AuroraWorkText(
                event.operationLabel(), AuroraWorkRole.Summary, Modifier.toolOperationColumn(separatorGap),
                color = AuroraColors.DesktopWorkMuted, maxLines = 1,
            )
            AuroraWorkText(
                title, AuroraWorkRole.Action, Modifier.weight(1f),
                color = AuroraColors.DesktopInk, maxLines = if (expanded && event.command == null) Int.MAX_VALUE else 1,
            )
        }
        if (event.detailsFormat == DetailFormat.Diff && status == StepStatus.Done) {
            AuroraWorkText(
                event.summary, AuroraWorkRole.Metadata, Modifier.padding(end = AuroraLayout.Space),
                color = AuroraColors.DesktopWorkMuted, maxLines = 1,
            )
        }
        AuroraWorkText(
            status.label, AuroraWorkRole.Metadata,
            Modifier.graphicsLayer { alpha = if (showStatus) 1f else 0f },
            color = when (status) {
                StepStatus.Done -> AuroraColors.Green
                StepStatus.Failed -> AuroraColors.Error
                else -> AuroraColors.DesktopWorkMuted
            },
            maxLines = 1,
        )
    }
}

private fun Modifier.toolOperationColumn(separatorGap: Dp): Modifier = layout { measurable, constraints ->
    val label = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val columnWidth = label.width + separatorGap.roundToPx()
    val titleMinimum = AuroraLayout.DesktopTarget.roundToPx()
    if (constraints.maxWidth >= columnWidth + titleMinimum) {
        layout(columnWidth, label.height) { label.placeRelative(0, 0) }
    } else layout(0, 0) {}
}

private fun Modifier.workIconOutline(glyph: HbIconGlyph): Modifier = layout { measurable, constraints ->
    // Visible outline bounds in the bundled vector viewports, including round stroke caps.
    val (from, to) = when (glyph) {
        HbIconGlyph.Search -> 2f / 24f to 22f / 24f
        HbIconGlyph.Document -> 3f / 24f to 21f / 24f
        HbIconGlyph.Thinking -> 1f / 24f to 23f / 24f
        HbIconGlyph.Terminal -> 1f / 16f to 15f / 16f
        else -> 0f to 1f
    }
    val icon = measurable.measure(constraints)
    val width = constraints.constrainWidth(AuroraLayout.DesktopWorkIconColumn.roundToPx())
    val outlineWidth = (to - from) * icon.width
    val scale = if (outlineWidth > width) width / outlineWidth else 1f
    layout(width, icon.height) {
        icon.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0.5f)
            scaleX = scale
            scaleY = scale
            translationX = -from * icon.width * scale
        }
    }
}

@Composable
private fun AuroraWorkDetailsBody(event: WorkEvent, touch: Boolean, modifier: Modifier, fileContent: @Composable () -> Unit) {
    val desktopTool = !touch && event.kind != WorkEventKind.Thinking
    val verticalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.Space
    val label = event.detailLabel
    val selection = rememberSelectionState()
    val divider = if (touch) {
        Modifier.drawBehind {
            val stroke = AuroraLayout.Stroke.toPx()
            val y = stroke / 2
            drawLine(AuroraColors.WorkDivider, Offset(0f, y), Offset(size.width, y), stroke)
        }
    } else Modifier
    Column(
        modifier = modifier.fillMaxWidth().then(divider).padding(
            start = if (touch) AuroraLayout.WorkTextInset else AuroraLayout.Space,
            top = if (desktopTool) AuroraLayout.SmallGap else verticalInset,
            bottom = verticalInset,
        ),
    ) {
        if (event.isCommand && event.command != null ||
            (event.detailsFormat == DetailFormat.Code || event.detailsFormat == DetailFormat.Diff) && event.details.isNotBlank()
        ) {
            AuroraWorkCode(event, touch)
            fileContent()
            return@Column
        }
        if (desktopTool) {
            Box(
                Modifier.fillMaxWidth().padding(end = AuroraLayout.SmallGap)
                    .heightIn(min = AuroraLayout.DesktopWorkCopyTarget),
                contentAlignment = Alignment.CenterStart,
            ) {
                AuroraWorkCopy(event.details, Modifier.align(Alignment.TopEnd))
                if (label != null) {
                    SelectionContainer(
                        Modifier.padding(end = AuroraLayout.DesktopWorkCopyTarget + AuroraLayout.Space),
                    ) {
                        AuroraWorkText(label, AuroraWorkRole.Metadata, color = AuroraColors.DesktopWorkMuted)
                    }
                }
            }
        }
        SelectionContainer(
            state = selection,
            modifier = Modifier.fillMaxWidth().padding(end = if (touch) AuroraLayout.ContentInset else AuroraLayout.Space),
        ) {
            Column {
                if (!desktopTool && label != null) {
                    AuroraWorkText(
                        label, AuroraWorkRole.Action,
                        Modifier.padding(bottom = AuroraLayout.Space),
                        color = if (touch) AuroraColors.WorkMuted else AuroraColors.DesktopInk,
                    )
                }
                event.command?.let { command ->
                    AuroraReplyText(
                        command, WorkDetailWidth.padding(bottom = AuroraLayout.SmallGap),
                        HbTypography.Code.copy(color = if (touch) AuroraColors.WorkInk else AuroraColors.DesktopInk),
                    )
                }
                if (event.details.isNotBlank() || event.files.isEmpty()) {
                    AuroraWorkDetailContent(event, touch, selection)
                }
            }
        }
        fileContent()
    }
}

@Composable
private fun AuroraWorkCode(event: WorkEvent, touch: Boolean) {
    val diff = event.detailsFormat == DetailFormat.Diff
    val command = event.command.takeIf { event.isCommand }
    var wrap by rememberSaveable(event.id) { mutableStateOf(false) }
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    val prepared by produceState<Pair<String, AuroraPreparedCode>?>(null, event) {
        value = withContext(Dispatchers.Default) {
            val source = when {
                command != null -> event.commandCodeDetails()
                diff -> event.diffCodeDetails()
                else -> event.readCodeDetails()
            }
            source.heading to prepareAuroraCode(event.details, source.lines, previous = null)
        }
    }
    Row(Modifier.fillMaxWidth().padding(end = AuroraLayout.SmallGap), verticalAlignment = Alignment.CenterVertically) {
        AuroraWorkText(
            prepared?.first ?: if (command == null) "Фрагмент файла" else "Вывод команды", AuroraWorkRole.Metadata,
            Modifier.weight(1f), AuroraColors.DesktopWorkMuted,
        )
        SourceAction(
            AuroraSourceAction.Wrap, touch, { wrap = !wrap },
            modifier = Modifier.size(if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopWorkCopyTarget),
            selected = wrap,
        )
        val output = if (command == null) event.details else listOfNotNull(
            event.detailLabel?.let { "$it>" }, command, event.details.takeIf(String::isNotBlank),
        ).joinToString("\n")
        AuroraWorkCopy(output)
    }
    val code = prepared?.second
    if (code == null) {
        AuroraWorkText("Подготавливаем код…", AuroraWorkRole.Detail, WorkDetailWidth)
    } else {
        SelectionContainer(WorkMarkdownIntrinsicSize.padding(end = AuroraLayout.Space)) {
            if (diff || command != null) {
                BoxWithConstraints {
                    AuroraCodeViewport(
                        code, wrap, touch, modal = false, horizontal, vertical, streaming = event.status == StepStatus.Current,
                        decoratedViewportWidth = maxWidth, separatorBeforeLine = command?.lineSequence()?.count(),
                    )
                }
            } else {
                AuroraCodeViewport(code, wrap, touch, modal = false, horizontal, vertical, streaming = false)
            }
        }
    }
}

@Composable
private fun AuroraWorkCopy(output: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val label = "Копировать вывод"
    val enabled = output.isNotBlank()
    var copies by remember(output) { mutableIntStateOf(0) }
    if (copies > 0) LaunchedEffect(copies) {
        delay(HbMotions.Default.copyFeedbackMs.toLong())
        copies = 0
    }
    HbTooltip(label, modifier, enabled = enabled) {
        AuroraControl(
            onClick = { if (runCatching { clipboard.setText(AnnotatedString(output)) }.isSuccess) copies++ },
            modifier = Modifier.size(AuroraLayout.DesktopWorkCopyTarget).semantics {
                if (copies > 0) {
                    stateDescription = "Скопировано"
                    liveRegion = LiveRegionMode.Polite
                }
            },
            label = label,
            enabled = enabled,
            round = false,
            minimumTarget = AuroraLayout.DesktopWorkCopyTarget,
            horizontalPadding = AuroraLayout.SmallGap,
            background = Color.Transparent,
            hoverBackground = AuroraColors.MessageActionHover,
        ) {
            HbIcon(
                if (copies > 0) HbIconGlyph.Check else HbIconGlyph.Copy,
                tint = if (enabled) AuroraColors.DesktopInk else AuroraColors.DesktopWorkMuted,
                size = AuroraLayout.Glyph,
            )
        }
    }
}

private val WorkEvent.shellName: String?
    get() = title?.takeIf { it.lowercase() in ShellToolTitles }

private val ShellToolTitles = setOf("bash", "powershell", "pwsh")

private val WorkEvent.isCommand: Boolean
    get() = operation == ToolOperation.EXECUTE || (operation == null || operation == ToolOperation.OTHER) && shellName != null

private val WorkEvent.commandPreview: String?
    get() = command?.lineSequence()?.firstOrNull { it.isNotBlank() }

private fun WorkEvent.operationLabel(): String {
    if (isCommand) return shellName?.takeIf { command != null }?.let { "$it ·" } ?: "Команда ·"
    return when (operation) {
        ToolOperation.READ -> "Чтение ·"
        ToolOperation.EDIT -> "Правка ·"
        ToolOperation.DELETE -> "Удаление ·"
        ToolOperation.MOVE -> "Перемещение ·"
        ToolOperation.SEARCH -> "Поиск ·"
        ToolOperation.EXECUTE -> "Команда ·"
        ToolOperation.THINK -> "Анализ ·"
        ToolOperation.FETCH -> "Загрузка ·"
        ToolOperation.SWITCH_MODE -> "Режим ·"
        ToolOperation.OTHER -> "Вызов ·"
        null -> when (kind) {
            WorkEventKind.File -> "Файл ·"
            WorkEventKind.Search -> "Поиск ·"
            WorkEventKind.Check -> "Вызов ·"
            WorkEventKind.Thinking -> "Размышления"
        }
    }
}

private fun WorkEvent.glyph() = if (isCommand) HbIconGlyph.Terminal else when (kind) {
    WorkEventKind.File -> HbIconGlyph.Document
    WorkEventKind.Search, WorkEventKind.Check -> HbIconGlyph.Search
    WorkEventKind.Thinking -> HbIconGlyph.Thinking
}
