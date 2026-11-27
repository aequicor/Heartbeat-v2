package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import io.github.aequicor.heartbeat.designsystem.components.HbAuroraOutline
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import kotlinx.coroutines.CoroutineScope

internal class AuroraWorkAccess(startedLive: Boolean) {
    val requester = FocusRequester()
    val hover = MutableInteractionSource()
    var focusWithinReply by mutableStateOf(false)
        private set
    var restoringAfterToggle = false
    var headerHeight by mutableIntStateOf(0)
    val historyHeight = AuroraMeasuredHeight()
    val answerHeight = AuroraMeasuredHeight()
    val displayedHistoryHeight get() = historyHeight.displayed
    private var pendingEvent: EventDisclosure? = null
    var continuedFromStream by mutableStateOf(startedLive)
        private set

    fun finishStreamResize() {
        continuedFromStream = false
    }

    fun group(modifier: Modifier): Modifier = modifier
        .onFocusChanged { focusWithinReply = it.hasFocus }
        .focusGroup()

    fun beforeToggle(
        id: String,
        clearance: State<Int>?,
        reflow: AuroraHistoryReflow?,
        scope: CoroutineScope,
    ) {
        restoringAfterToggle = true
        finishStreamResize()
        reflow?.beforeToggle(id, displayedHistoryHeight, answerHeight.displayed, clearance, scope)
    }

    fun expectEventDisclosure(action: AiStudioAction.ToggleWorkEvent, expanded: Set<String>, scrollInput: Int) {
        val key = "${action.replyId}/${action.stepId}/${action.eventId}"
        pendingEvent = EventDisclosure(key, key !in expanded, scrollInput)
    }

    fun isApplyingEventDisclosure(expanded: Set<String>, scrollInput: Int): Boolean =
        pendingEvent?.let { it.open == (it.key in expanded) && it.scrollInput == scrollInput } == true

    fun restoreEventAnchor(expanded: Set<String>, id: String, reflow: AuroraHistoryReflow?, scope: CoroutineScope) {
        val request = pendingEvent ?: return
        if (request.open != (request.key in expanded)) return
        pendingEvent = null
        if (reflow != null && request.scrollInput == reflow.tail.inputGeneration) {
            reflow.beforeToggle(id, displayedHistoryHeight, answerHeight.displayed, null, scope)
        }
    }

    private data class EventDisclosure(val key: String, val open: Boolean, val scrollInput: Int)
}

@Composable
internal fun AuroraReplyWork(
    reply: Reply,
    expanded: Boolean,
    touch: Boolean,
    onToggle: () -> Unit,
    focus: AuroraWorkAccess,
    reflow: AuroraHistoryReflow?,
    finalResize: Boolean,
    onResizeEnd: () -> Unit,
    expandedThinking: Boolean,
    work: @Composable () -> Unit,
) {
    val finished = reply.status != ReplyStatus.Working
    val hasWork = reply.steps.isNotEmpty()
    val settled = !finalResize && focus.displayedHistoryHeight == 0
    if (finished && !hasWork && settled) return
    val scope = rememberCoroutineScope()
    val streaming = expandedThinking && reflow?.ownsWorkResize(reply.id) != true
    val resize = auroraWorkHeightMotion(focus, finalResize, onResizeEnd, streaming) { height, target ->
        reflow?.workResized(reply.id, height, target, scope)
    }
    Column(Modifier.trackReplyArrivalBlock().then(resize)) {
        if (finished && hasWork && touch) {
            AuroraWorkHeader(reply.steps.size, expanded, onToggle, Modifier.focusRequester(focus.requester))
        }
        if (!finished || hasWork && expanded) {
            work()
            if (finished) {
                Box(Modifier.fillMaxWidth().height(AuroraLayout.Stroke).background(AuroraColors.MessageDivider))
            }
        }
    }
}

@Composable
private fun AuroraWorkHeader(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = if (expanded) Color.Transparent else AuroraColors.WorkAccess
    AuroraControl(onClick, modifier.fillMaxWidth(), touch = true, background = surface, round = false) {
        AuroraWorkChevron(expanded, tint = AuroraColors.Muted)
        AuroraWorkText(
            auroraWorkTitle(count), AuroraWorkRole.Action,
            Modifier.padding(start = AuroraLayout.Space), color = AuroraColors.WorkMuted,
        )
    }
}

private const val WorkHoverExitDelayMillis = 500L

@Composable
internal fun rememberAuroraWorkReveal(
    hovered: Boolean,
    hoverOverMenu: Boolean,
    focusVisible: Boolean,
    selectingText: Boolean,
): AuroraMessageReveal {
    val pinned = focusVisible
    val reveal = rememberAuroraMessageReveal(
        hovered = hovered || hoverOverMenu,
        pinned = pinned,
        dwellOnTarget = hovered,
        exitDelayMillis = WorkHoverExitDelayMillis,
        holdHover = selectingText,
    )
    val pinnedOpacity = rememberUpdatedState(1f)
    return if (pinned) reveal.copy(opacity = pinnedOpacity) else reveal
}

@Composable
internal fun RestoreAuroraWorkFocus(access: AuroraWorkAccess, hasWork: Boolean, expanded: Boolean, finished: Boolean) {
    val focusedAtCompletion = finished && access.focusWithinReply
    SideEffect(expanded, finished) {
        if (hasWork && (access.restoringAfterToggle || focusedAtCompletion)) access.requester.requestFocus()
        access.restoringAfterToggle = false
    }
}

@Composable
internal fun AuroraReplyLayout(
    history: @Composable () -> Unit,
    answer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expandToFitHistory: Boolean = false,
) {
    Layout(contents = listOf(history, answer), modifier = modifier) { (historySlot, answerSlot), constraints ->
        val workContent = historySlot.singleOrNull()
        val withoutMinimumHeight = constraints.copy(minHeight = 0)
        val minimumWidth = if (expandToFitHistory && workContent != null) {
            constraints.constrainWidth(workContent.maxIntrinsicWidth(Constraints.Infinity))
        } else {
            constraints.minWidth
        }
        val reply = answerSlot.single().measure(withoutMinimumHeight.copy(minWidth = minimumWidth))
        val work = workContent?.measure(
            withoutMinimumHeight.copy(minWidth = reply.width, maxWidth = reply.width),
        )
        val workHeight = work?.height ?: 0
        layout(reply.width, workHeight + reply.height) {
            work?.placeRelative(0, 0)
            reply.placeRelative(0, workHeight)
        }
    }
}

@Composable
internal fun Modifier.auroraExpandedWorkSurface(headerHeight: Int): Modifier {
    val above = with(LocalDensity.current) {
        if (headerHeight > 0) headerHeight.toDp() else AuroraLayout.DesktopWorkHeaderHeight
    }
    return layout { measurable, constraints ->
        // The header occupies the same space above the reply in hover and expanded states.
        val header = above.roundToPx()
        val surface = measurable.measure(constraints.offset(vertical = header))
        layout(surface.width, (surface.height - header).coerceAtLeast(0)) {
            surface.placeRelative(0, -header)
        }
    }.then(HbAuroraOutline(AuroraColors.AgentMessage, AuroraColors.MessageOutline, AuroraLayout.MessageRadius))
        .padding(top = above)
}

@Composable
internal fun AuroraWorkKeyboardAccess(
    title: String,
    expanded: Boolean,
    requester: FocusRequester,
    onFocus: (Boolean) -> Unit,
    onToggle: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = AuroraLayout.DesktopWorkHeaderHeight)
            .semantics {
                contentDescription = title
                stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
                role = Role.Button
                onClick { onToggle(); true }
            }
            .focusRequester(requester).onFocusChanged { onFocus(it.isFocused) }
            .toggleWorkOnKey(onToggle)
            .focusable(),
    )
}

@Composable
internal fun auroraWorkHeaderMinWidth(title: String): Dp {
    val text = rememberTextMeasurer().measure(title, style = HbTypography.Caption, maxLines = 1)
    return with(LocalDensity.current) {
        (text.size.width + AuroraLayout.Glyph.roundToPx() + AuroraLayout.Space.roundToPx() * 3).toDp()
    }
}

@Composable
internal fun AuroraDesktopWorkHeader(
    title: String,
    expanded: Boolean,
    focusVisible: Boolean,
    interaction: MutableInteractionSource,
    selection: SelectionState,
    reveal: AuroraMessageReveal,
    onToggle: () -> Unit,
    onTextFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    workMenu: AuroraWorkMenu? = null,
) {
    val headerInteraction = remember { MutableInteractionSource() }
    val hovered by headerInteraction.collectIsHoveredAsState()
    val highlight = hovered || selection.selectedTexts.isNotEmpty()
    val background = if (highlight) AuroraColors.WorkAccessHover else AuroraColors.WorkAccess
    val outline = if (focusVisible) AuroraColors.Primary else AuroraColors.MessageOutline
    val stroke = if (focusVisible) AuroraLayout.FrameStroke else AuroraLayout.Stroke
    val toggle by rememberUpdatedState(onToggle)
    // The overlay is outside the bubble's layout bounds. Pass its click to the same menu in root coordinates.
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pointer = if (reveal.interactive) {
        Modifier.onGloballyPositioned { coordinates = it }.pointerInput(workMenu) {
            awaitEachGesture {
                // Observe taps before text selection handles the press. Drag consumption still cancels the tap.
                // Compose's awaitFirstDown only accepts the primary mouse button on Desktop.
                var down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.changedToDown() }
                while (down == null) {
                    down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.changedToDown() }
                }
                if (currentEvent.buttons.isSecondaryPressed) {
                    val open = workMenu?.openAt
                    val header = coordinates
                    if (open != null && header?.isAttached == true) {
                        down.consume()
                        open(header.localToRoot(down.position))
                    }
                    return@awaitEachGesture
                }
                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                waitForUpOrCancellation(PointerEventPass.Initial)?.let {
                    it.consume()
                    toggle()
                }
            }
        }
    } else Modifier
    val surface = HbAuroraOutline(background, outline, AuroraLayout.MessageRadius, Dp.Hairline, stroke)
    Row(
        Modifier.fillMaxWidth().aboveMessage().heightIn(min = AuroraLayout.DesktopWorkHeaderHeight)
            .then(modifier)
            .graphicsLayer { alpha = reveal.opacity.value }
            .then(surface)
            .hoverable(interaction, enabled = reveal.interactive)
            .hoverable(headerInteraction, enabled = reveal.interactive)
            .then(pointer)
            .toggleWorkOnKey(onToggle)
            .semantics { hideFromAccessibility() }
            .padding(horizontal = AuroraLayout.Space, vertical = AuroraLayout.SmallGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuroraWorkChevron(expanded, tint = AuroraColors.WorkMuted)
        val inset = Modifier.padding(start = AuroraLayout.Space)
        if (reveal.interactive) {
            SelectionContainer(modifier = inset.onFocusChanged { onTextFocus(it.isFocused) }, state = selection) {
                AuroraWorkText(title, AuroraWorkRole.Summary, color = AuroraColors.WorkMuted, maxLines = 1)
            }
        } else AuroraWorkText(title, AuroraWorkRole.Summary, inset, color = AuroraColors.WorkMuted, maxLines = 1)
    }
}

private fun Modifier.toggleWorkOnKey(onToggle: () -> Unit): Modifier = onKeyEvent { event ->
    when (event.key) {
        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
            if (event.type == KeyEventType.KeyUp) onToggle()
            true
        }
        else -> false
    }
}

internal fun auroraWorkTitle(count: Int): String =
    if (count == 0) "Ход работы" else "Ход работы · ${auroraWorkCount(count, "этап", "этапа", "этапов")}"

internal fun auroraWorkCount(count: Int, one: String, few: String, many: String): String {
    val units = when {
        count % 100 in 11..14 -> many
        count % 10 == 1 -> one
        count % 10 in 2..4 -> few
        else -> many
    }
    return "$count $units"
}

private fun Modifier.aboveMessage() = layout { measurable, constraints ->
    val header = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(header.width, 0) { header.place(0, -header.height) }
}
