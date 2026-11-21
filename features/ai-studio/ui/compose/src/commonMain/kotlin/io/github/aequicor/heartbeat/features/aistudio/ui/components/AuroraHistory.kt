@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbAuroraOutline
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun AuroraHistory(
    state: AiStudioScreenState,
    liveText: StateFlow<LiveText>,
    list: LazyListState,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    readingWidth: Dp = Dp.Infinity,
    viewportHeight: Dp = Dp.Hairline,
    tailFollow: AuroraTailFollow? = null,
) {
    val live = state.live
    val history = state.messages
    val prepend = if (live == null) 0 else 1
    fun messageAt(index: Int): Message = if (index == 0 && live != null) live else history[index - prepend]
    val usableHeight = (viewportHeight - padding.calculateTopPadding() - padding.calculateBottomPadding())
        .coerceAtLeast(Dp.Hairline)
    val currentHeight = rememberUpdatedState(usableHeight)
    val reflow = remember(list, live?.id ?: history.firstOrNull()?.id, tailFollow) {
        AuroraHistoryReflow(list, tailFollow ?: AuroraTailFollow(list)) { currentHeight.value }
    }
    LaunchedEffect(list, reflow.tail) {
        snapshotFlow {
            list.canScrollBackward to list.isScrollInProgress
        }.collect { reflow.tail.observePosition() }
    }
    val adjustment = reflow.ready(state.expandedWork)
    val density = LocalDensity.current
    val insets = remember(padding, reflow, adjustment, density) {
        with(density) {
            padding.withAuroraReflowInsets(
                tail = { reflow.tailPixels(adjustment).toDp() },
                above = { reflow.shortHistoryTopShift.toDp() },
            )
        }
    }
    if (adjustment != null) SideEffect { reflow.apply(adjustment) }
    LazyColumn(
        modifier = modifier.nestedScroll(reflow),
        state = list,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(if (touch) AuroraLayout.MessageGap else Dp.Hairline, Alignment.Top),
        contentPadding = insets,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(
            count = history.size + prepend,
            key = { messageAt(it).id },
            contentType = { if (messageAt(it) is User) "user" else "reply" },
        ) { index ->
            val message = messageAt(index)
            val older = history.getOrNull(index + 1 - prepend)
            AuroraMessageRow(
                message, state.expandedWork, state.expandedSteps, state.expandedEvents,
                touch, onAction, readingWidth, reflow,
                modifier = Modifier.padding(top = message.auroraGapBefore(older, touch)),
                liveText = liveText.takeIf { message.id == live?.id },
            )
        }
    }
}
@Composable
private fun AuroraMessageRow(
    message: Message,
    expandedWork: Set<String>,
    expandedSteps: Set<String>,
    expandedEvents: Set<String>,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    readingWidth: Dp,
    reflow: AuroraHistoryReflow,
    modifier: Modifier = Modifier,
    liveText: StateFlow<LiveText>? = null,
) {
    var workAccessVisible by remember(message.id) { mutableStateOf(false) }
    val selection = rememberSelectionState()
    AuroraMessageMenu(
        message, touch, onAction, readingWidth, selection, modifier.zIndex(if (workAccessVisible) 1f else 0f), liveText,
    ) { hoverOverMenu ->
        when (message) {
            is User -> AuroraUserBubble(message, touch, onAction, selection)
            is Reply -> if (message.showArchivedAnswer(liveText)) {
                AuroraArchivedAnswer(message, touch, onAction, selection, reflow)
                if (workAccessVisible) SideEffect { workAccessVisible = false }
            } else AuroraAgentMessage(
                message, message.id in expandedWork, expandedSteps, expandedEvents, touch, onAction,
                liveText = liveText, selection = selection, hoverOverMenu = hoverOverMenu,
                reflow = reflow,
                onWorkAccessVisibility = { workAccessVisible = it },
            )
        }
    }
}

@Composable
private fun Reply.showArchivedAnswer(liveText: StateFlow<LiveText>?): Boolean {
    val answerOnly = status == ReplyStatus.Completed && steps.isEmpty() && liveText == null
    // Replies that started live retain their existing selection and resize animation at completion.
    return remember(id) { answerOnly } && answerOnly
}

@Composable
private fun AuroraArchivedAnswer(
    reply: Reply, touch: Boolean, onAction: (AiStudioAction) -> Unit, selection: SelectionState,
    reflow: AuroraHistoryReflow,
) {
    val outline = HbAuroraOutline(AuroraColors.AgentMessage, AuroraColors.MessageOutline, AuroraLayout.MessageRadius)
    val reveal = if (supportsAuroraStagedRichLayout() && reply.historyContent?.plain == false) {
        remember(reply.id) { ArchiveReveal() }
    } else null
    Box(Modifier.widthIn(max = AuroraLayout.AgentBubble).focusGroup().then(outline)
        .trackReplyHeight(reply, reflow, selection, reply.historyContent?.plain == false)) {
        AuroraReplyAnswer(reply, touch, onAction, null, selection, false, {}, reflow.viewportHeight, reveal)
    }
}

@Composable
internal fun AuroraUserBubble(
    user: User, touch: Boolean, onAction: (AiStudioAction) -> Unit, selection: SelectionState,
) {
    val surface = if (user.delivery == Delivery.Failed) {
        HbAuroraOutline(AuroraColors.User, AuroraColors.Error, AuroraLayout.MessageRadius)
    } else {
        Modifier.background(AuroraColors.User, RoundedCornerShape(AuroraLayout.MessageRadius))
    }
    val textPresent = user.text.isNotEmpty()
    val width = if (!touch && user.files.isEmpty()) AuroraLayout.DesktopMessageBubble else AuroraLayout.UserBubble
    val horizontalInset = if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageHorizontalInset
    AuroraMessageTimestamp(
        user.time,
        inlineAllowed = false,
        textSetsWidth = user.files.isEmpty() && textPresent,
        modifier = Modifier.widthIn(max = width).then(surface).padding(
            start = horizontalInset, end = horizontalInset,
            top = if (touch) AuroraLayout.Space else AuroraLayout.DesktopMessageVerticalInset,
        ),
        bottomPadding = AuroraLayout.DesktopMessageVerticalInset.takeUnless { touch },
        status = { AuroraDeliveryStatus(user, touch) },
    ) { onTextLayout ->
        Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            user.files.forEach { file ->
                AuroraControl(
                    { onAction(AiStudioAction.OpenFile(file.id)) }, touch = touch,
                    background = Color.Transparent, round = false,
                ) {
                    AuroraFileLabel(file, Modifier.padding(vertical = AuroraLayout.SmallGap))
                }
            }
            if (textPresent) SelectionContainer(state = selection) {
                BasicText(user.text, style = auroraMessageStyle(), onTextLayout = onTextLayout)
            }
        }
    }
}

@Composable
private fun AuroraDeliveryStatus(user: User, touch: Boolean) {
    when (user.delivery) {
        Delivery.Failed -> AuroraMenu(listOf(HbMenuAction(user.error.orEmpty(), {}, enabled = false))) {
            trigger, open ->
            AuroraControl(
                open, trigger, label = "Причина ошибки отправки", touch = touch,
                background = Color.Transparent, round = false,
            ) {
                AuroraText("Ошибка отправки  ⓘ", small = true, color = AuroraColors.Error)
            }
        }
        Delivery.Sending -> AuroraText("Отправляем…", small = true, color = AuroraColors.MessageMuted)
        Delivery.Sent -> Unit
    }
}

@Composable
internal fun AuroraAgentMessage(
    reply: Reply,
    expanded: Boolean,
    expandedSteps: Set<String>,
    expandedEvents: Set<String>,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    liveText: StateFlow<LiveText>? = null,
    selection: SelectionState = rememberSelectionState(),
    hoverOverMenu: Boolean = false,
    reflow: AuroraHistoryReflow? = null,
    onWorkAccessVisibility: (Boolean) -> Unit = {},
) {
    val completed = reply.status == ReplyStatus.Completed
    val hasWork = reply.steps.isNotEmpty()
    val focus = remember(reply.id) { AuroraWorkAccess(liveText != null) }
    val desktopHistory = hasWork && completed && !touch
    val animated = HbTheme.motion != HbMotionPreference.Reduced
    val clearance = if (desktopHistory && reflow != null) {
        rememberAuroraWorkHeaderClearance(focus.headerHeight, expanded)
    } else null
    val onToggle = {
        focus.beforeToggle(reply.id, expanded, clearance, reflow, animated)
        onAction(AiStudioAction.ToggleWork(reply.id))
    }
    RestoreAuroraWorkFocus(focus, hasWork, expanded, completed)

    var accessFocused by remember { mutableStateOf(false) }
    val visibleFocus = accessFocused && HbFocusIndication.keyboardNavigation
    val reveal = if (desktopHistory) {
        val hovered by focus.hover.collectIsHoveredAsState()
        rememberAuroraWorkReveal(hovered, hoverOverMenu, expanded || visibleFocus, selection.selectedTexts.isNotEmpty())
            .takeIf { it.drawn }
    } else null
    val accessVisible = reveal != null
    val surface = focus.replySurface(
        expandedHistory = desktopHistory && expanded,
        headerOpacity = reveal?.let { header -> { header.opacity.value } },
    )
    val hoverTarget = if (desktopHistory) Modifier.hoverable(focus.hover) else Modifier
    val roomAbove = if (clearance != null) Modifier.auroraWorkHeaderClearance(clearance) else Modifier
    SideEffect { onWorkAccessVisibility(accessVisible) }
    Box(focus.group(modifier.widthIn(max = AuroraLayout.AgentBubble).then(roomAbove)).then(hoverTarget)) {
        if (desktopHistory) {
            Box(Modifier.matchParentSize()) {
                AuroraWorkKeyboardAccess(
                    auroraWorkTitle(reply.steps.size), expanded, focus.requester, { accessFocused = it }, onToggle,
                )
            }
        }
        AuroraAgentMessageBody(
            reply, expanded, touch, onAction, onToggle, liveText,
            selection, focus, reflow,
            modifier = surface,
        ) {
            AuroraWorkTimeline(reply, expandedSteps, expandedEvents, touch, onAction, onDisclosure = {
                reflow?.beforeToggle(reply.id, expanded, focus.displayedHistoryHeight, animated, clearance = null)
            })
        }
        if (reveal != null) {
            Box(Modifier.matchParentSize()) {
                AuroraDesktopWorkHeader(
                    auroraWorkTitle(reply.steps.size), expanded, visibleFocus, focus.hover, reveal, onToggle,
                    modifier = Modifier.onSizeChanged { focus.headerHeight = it.height },
                )
            }
        }
    }
}

@Composable
private fun AuroraAgentMessageBody(
    reply: Reply,
    expanded: Boolean,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    onToggle: () -> Unit,
    liveText: StateFlow<LiveText>?,
    selection: SelectionState,
    focus: AuroraWorkAccess,
    reflow: AuroraHistoryReflow?,
    modifier: Modifier = Modifier,
    work: @Composable () -> Unit,
) {
    val finalResize = reply.status == ReplyStatus.Completed && focus.continuedFromStream
    val tracksHeight = liveText != null || finalResize || reply.historyContent?.plain == false
    val surface = modifier.trackReplyHeight(reply, reflow, selection, tracksHeight)
    val onResizeEnd = { if (finalResize) focus.finishStreamResize() }
    AuroraReplyLayout(
        history = {
            AuroraReplyWork(reply, expanded, touch, onToggle, focus, reflow, finalResize, onResizeEnd, work)
        },
        answer = {
            AuroraReplyAnswer(
                reply, touch, onAction, liveText, selection, finalResize, onResizeEnd,
                viewportHeight = reflow?.viewportHeight ?: Dp.Hairline,
            )
        },
        modifier = surface,
        expandToFitHistory = expanded && reply.status == ReplyStatus.Completed && reply.steps.isNotEmpty(),
    )
}

@Composable
private fun Modifier.trackReplyHeight(
    reply: Reply, reflow: AuroraHistoryReflow?, selection: SelectionState, tracksHeight: Boolean,
): Modifier {
    val scope = rememberCoroutineScope()
    val tracker = remember(reply.id) { ReplyHeightTracker() }
    return layout { measurable, constraints ->
        // Final Markdown preparation can finish after the stream's first completed frame.
        tracker.tracked = tracker.tracked || tracksHeight
        tracker.before = Snapshot.withoutReadObservation { reflow?.tail?.beforeResize() }
        // A child resize can settle the disclosure anchor during this measure.
        // Preserve its ownership so the same delta is not compensated twice.
        tracker.workOwned = reflow?.ownsWorkResize(reply.id) == true
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }.onSizeChanged { size ->
        val previous = tracker.height
        tracker.height = size.height
        if (tracker.workOwned) return@onSizeChanged
        val before = tracker.before
        if (previous > 0 && tracker.tracked && before != null) {
            reflow?.streamingReplyResized(
                reply.id, size.height - previous, before, scope, selection.selectedTexts.isNotEmpty(),
            )
        }
    }
}

private class ReplyHeightTracker {
    var height = 0
    var tracked = false
    var workOwned = false
    var before: AuroraTailFollow.BeforeResize? = null
}

@Composable
private fun AuroraWorkAccess.replySurface(expandedHistory: Boolean, headerOpacity: (() -> Float)?): Modifier {
    if (expandedHistory) {
        // Detail disclosure must not change the width of the desktop work card.
        return Modifier.auroraExpandedWorkSurface(headerHeight).fillMaxWidth()
    }
    val radius = AuroraLayout.MessageRadius
    return if (headerOpacity == null) {
        HbAuroraOutline(AuroraColors.AgentMessage, AuroraColors.MessageOutline, radius)
    } else {
        HbAuroraOutline.joiningHeader(AuroraColors.AgentMessage, AuroraColors.MessageOutline, radius, headerOpacity)
    }
}
