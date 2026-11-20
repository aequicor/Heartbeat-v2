package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import io.github.aequicor.heartbeat.designsystem.components.HbAuroraOutline
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus

internal class AuroraWorkAccess(startedLive: Boolean) {
    val requester = FocusRequester()
    val hover = MutableInteractionSource()
    var focusWithinReply by mutableStateOf(false)
        private set
    var restoringAfterToggle = false
    var headerHeight by mutableIntStateOf(0)
    var displayedHistoryHeight = 0
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
        expanded: Boolean,
        clearance: State<Int>?,
        reflow: AuroraHistoryReflow?,
        animated: Boolean,
    ) {
        restoringAfterToggle = true
        finishStreamResize()
        reflow?.beforeToggle(id, expanded, displayedHistoryHeight, animated, clearance)
    }
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
    work: @Composable () -> Unit,
) {
    val completed = reply.status == ReplyStatus.Completed
    val hasWork = reply.steps.isNotEmpty()
    val resize = auroraWorkHeightMotion(focus, finalResize, onResizeEnd) { height, target ->
        reflow?.workResized(reply.id, expanded, height, target)
    }
    Column(resize) {
        if (completed && hasWork && touch) {
            AuroraWorkHeader(reply.steps.size, expanded, onToggle, Modifier.focusRequester(focus.requester))
        }
        if (hasWork && (!completed || expanded)) {
            work()
            if (completed) {
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
        AuroraWorkAccessLabel(auroraWorkTitle(count), expanded, touch = true)
    }
}

private const val WorkHoverExitDelayMillis = 500L

@Composable
internal fun rememberAuroraWorkReveal(
    allowed: Boolean,
    hovered: Boolean,
    hoverOverMenu: Boolean,
    focusVisible: Boolean,
    selectingText: Boolean,
): AuroraMessageReveal {
    val pinned = allowed && focusVisible
    val reveal = rememberAuroraMessageReveal(
        hovered = allowed && (hovered || hoverOverMenu),
        pinned = pinned,
        dwellOnTarget = allowed && hovered,
        exitDelayMillis = if (allowed) WorkHoverExitDelayMillis else 0L,
        holdHover = selectingText,
    )
    val pinnedOpacity = rememberUpdatedState(1f)
    return if (pinned) reveal.copy(opacity = pinnedOpacity) else reveal
}

@Composable
internal fun RestoreAuroraWorkFocus(access: AuroraWorkAccess, hasWork: Boolean, expanded: Boolean, completed: Boolean) {
    val focusedAtCompletion = completed && access.focusWithinReply
    SideEffect(expanded, completed) {
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
        val workContent = historySlot.single()
        val withoutMinimumHeight = constraints.copy(minHeight = 0)
        val minimumWidth = if (expandToFitHistory) {
            constraints.constrainWidth(workContent.maxIntrinsicWidth(Constraints.Infinity))
        } else {
            constraints.minWidth
        }
        val reply = answerSlot.single().measure(withoutMinimumHeight.copy(minWidth = minimumWidth))
        val work = workContent.measure(
            withoutMinimumHeight.copy(minWidth = reply.width, maxWidth = reply.width),
        )
        layout(reply.width, work.height + reply.height) {
            work.placeRelative(0, 0)
            reply.placeRelative(0, work.height)
        }
    }
}

@Composable
internal fun Modifier.auroraExpandedWorkSurface(headerHeight: Int): Modifier {
    val above = with(LocalDensity.current) {
        if (headerHeight > 0) headerHeight.toDp() else AuroraLayout.DesktopWorkDisclosureTarget
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
        Modifier.fillMaxWidth().heightIn(min = AuroraLayout.DesktopWorkDisclosureTarget)
            .semantics {
                contentDescription = title
                stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
                role = Role.Button
                onClick { onToggle(); true }
            }
            .focusRequester(requester).onFocusChanged { onFocus(it.isFocused) }
            .onKeyEvent { event ->
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (event.type == KeyEventType.KeyUp) onToggle()
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    )
}

@Composable
internal fun AuroraDesktopWorkHeader(
    title: String,
    expanded: Boolean,
    focusVisible: Boolean,
    interaction: MutableInteractionSource,
    reveal: AuroraMessageReveal,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerInteraction = remember { MutableInteractionSource() }
    val hovered by headerInteraction.collectIsHoveredAsState()
    val background = if (expanded) AuroraColors.AgentMessage else AuroraColors.WorkAccess
    val outline = if (focusVisible) AuroraColors.Primary else AuroraColors.Line
    val stroke = if (focusVisible) AuroraLayout.FrameStroke else AuroraLayout.Stroke
    val toggle by rememberUpdatedState(onToggle)
    val pointer = if (reveal.interactive) {
        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { toggle() }) }
    } else Modifier
    val surface = if (!expanded || focusVisible) {
        HbAuroraOutline(background, outline, AuroraLayout.MessageRadius, Dp.Hairline, stroke)
    } else Modifier
    Row(
        Modifier.fillMaxWidth().aboveMessage().heightIn(min = AuroraLayout.DesktopWorkDisclosureTarget)
            .then(modifier)
            .graphicsLayer { alpha = reveal.opacity.value }
            .then(surface)
            .drawBehind {
                if (hovered) {
                    inset(AuroraLayout.SmallGap.toPx()) {
                        drawRoundRect(
                            AuroraColors.WorkHover, cornerRadius = CornerRadius(AuroraLayout.SmallRadius.toPx()),
                        )
                    }
                }
            }
            .hoverable(interaction)
            .hoverable(headerInteraction, enabled = reveal.interactive)
            .then(pointer)
            .semantics { hideFromAccessibility() }
            .padding(horizontal = AuroraLayout.Space, vertical = AuroraLayout.SmallGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuroraWorkAccessLabel(title, expanded, touch = false)
    }
}

@Composable
private fun AuroraWorkAccessLabel(title: String, expanded: Boolean, touch: Boolean) {
    AuroraWorkChevron(expanded, tint = if (touch) AuroraColors.Muted else AuroraColors.DesktopInk)
    AuroraWorkText(
        title, AuroraWorkRole.Action,
        Modifier.padding(start = AuroraLayout.Space),
        color = if (touch) AuroraColors.WorkMuted else AuroraColors.DesktopInk,
    )
}

internal fun auroraWorkTitle(count: Int): String {
    val units = when {
        count % 100 in 11..14 -> "этапов"
        count % 10 == 1 -> "этап"
        count % 10 in 2..4 -> "этапа"
        else -> "этапов"
    }
    return "Ход работы · $count $units"
}

private fun Modifier.aboveMessage() = layout { measurable, constraints ->
    val header = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(header.width, 0) { header.place(0, -header.height) }
}
