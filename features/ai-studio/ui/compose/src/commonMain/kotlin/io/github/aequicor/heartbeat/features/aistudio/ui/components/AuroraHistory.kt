@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbAuroraOutline
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind
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
    preparedHistory: AuroraCompletedDocuments? = null,
) {
    val live = state.live
    val history = state.messages
    val prepend = if (live == null) 0 else 1
    fun messageAt(index: Int): Message = if (index == 0 && live != null) live else history[index - prepend]
    val usableHeight = (viewportHeight - padding.calculateTopPadding() - padding.calculateBottomPadding())
        .coerceAtLeast(Dp.Hairline)
    val currentHeight = rememberUpdatedState(usableHeight)
    val newest = live ?: history.firstOrNull()
    val tail = tailFollow ?: remember(list) { AuroraTailFollow(list) }
    val initialNewestId = remember(list) {
        val opened = state.openedHistory
        if (opened != null) opened.newestMessageId else newest?.id
    }
    val newestReply = newest as? Reply
    val incomingReply = newestReply?.takeUnless { it.id == initialNewestId }
    val reflow = remember(list, newest?.id, tail) {
        val openingAnchor = state.restoreAnchor.takeIf { newest?.id == initialNewestId }
        AuroraHistoryReflow(list, tail, openingAnchor, incomingReply?.id) { currentHeight.value }
    }
    SideEffect(newest?.id, tail) {
        val preceding = if (live == null) history.getOrNull(1) else history.firstOrNull()
        val beforePrompt = if (preceding is User && preceding.id == newestReply?.promptId) {
            history.getOrNull(if (live == null) 2 else 1)?.id
        } else null
        tail.newestChanged(newest?.id, preceding?.id, beforePrompt, newestReply != null, incomingReply != null)
    }
    LaunchedEffect(list, tail) {
        snapshotFlow {
            list.canScrollBackward to list.isScrollInProgress
        }.collect { tail.observePosition() }
    }
    if (newest is Reply) {
        ObserveAuroraReplyHeight(newest, liveText, list, reflow)
    }
    val density = LocalDensity.current
    val readingBottom = remember(list) { mutableFloatStateOf(0f) }
    val insets = remember(padding, reflow, density) {
        with(density) {
            padding.withAuroraReflowInsets(
                tail = { reflow.tailPixels().toDp() },
                above = { reflow.shortHistoryTopShift.toDp() },
            )
        }
    }
    val completedDocuments = preparedHistory ?: remember(list) { AuroraCompletedDocuments() }
    CompositionLocalProvider(LocalAuroraCompletedDocuments provides completedDocuments) {
        LazyColumn(
            modifier = modifier.nestedScroll(reflow).onGloballyPositioned {
                readingBottom.floatValue =
                    it.positionInRoot().y + it.size.height - with(density) { insets.calculateBottomPadding().toPx() }
            },
            state = list,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(
                if (touch) AuroraLayout.MessageGap else Dp.Hairline, Alignment.Top,
            ),
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
                val stream = liveText.takeIf { message.id == live?.id }
                val paddingBefore = Modifier.padding(top = message.auroraGapBefore(older, touch))
                AuroraReplyArrival(message, newestReply, stream, reflow, readingBottom, paddingBefore) { row ->
                    AuroraMessageRow(
                        message, state.expandedWork, state.expandedSteps, state.expandedEvents,
                        touch, onAction, readingWidth, reflow, modifier = row, liveText = stream,
                    )
                }
            }
        }
    }
}

private const val ReplyArrivalMillis = 960

@Composable
private fun AuroraReplyArrival(
    message: Message,
    newestReply: Reply?,
    liveText: StateFlow<LiveText>?,
    reflow: AuroraHistoryReflow,
    readingBottom: State<Float>,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val reply = newestReply?.takeIf { it.id == message.id }
    val tail = reflow.tail
    val progress = if (reply != null && HbTheme.motion != HbMotionPreference.Reduced) {
        remember(reply.id) { Animatable(0f) }
    } else null
    val revealReady = reply != null && tail.revealsReply(reply.id)
    if (progress != null && revealReady) LaunchedEffect(progress) {
        progress.animateTo(1f, tween(ReplyArrivalMillis, easing = LinearEasing))
    }
    val drawRow = if (reply == null) modifier else Modifier.drawWithContent {
        val live = liveText?.value
        val storedAnswerPlaced = reply.hasPlacedAnswer(reflow)
        val hasAnswer = storedAnswerPlaced || (live?.replyId == reply.id && live.text.isNotBlank())
        val completedAnswer = storedAnswerPlaced && reply.status == ReplyStatus.Completed
        if (hasAnswer && tail.waitsForReplyArrival(reply.id, size.height.toInt(), progress != null, completedAnswer)) {
            return@drawWithContent
        }
        drawContent()
    }.then(modifier)
    val revealing = if (progress != null) {
        remember(progress) { derivedStateOf { progress.value < 1f } }.value
    } else false
    val origin = if (revealing) remember(message.id) { mutableFloatStateOf(0f) } else null
    val rowBottom = if (revealing) remember(message.id) { mutableFloatStateOf(0f) } else null
    val front = if (origin != null && rowBottom != null && progress != null) remember(message.id, tail, readingBottom) {
        {
            if (!tail.revealsReply(message.id)) Float.POSITIVE_INFINITY
            else {
                val visibleHeight = (minOf(readingBottom.value, rowBottom.floatValue) - origin.floatValue)
                    .coerceAtLeast(0f)
                origin.floatValue + progress.value * visibleHeight
            }
        }
    } else null
    val paint = remember(front, tail, message.id) {
        front?.let { ReplyArrivalPaint({ tail.revealsReply(message.id) }, it) }
    }
    val row = if (origin == null || rowBottom == null) drawRow else drawRow.onGloballyPositioned {
        origin.floatValue = it.positionInRoot().y
        rowBottom.floatValue = origin.floatValue + it.size.height
    }
    CompositionLocalProvider(LocalAuroraReplyArrival provides paint) {
        content(row)
    }
}

@Composable
private fun ObserveAuroraReplyHeight(
    reply: Reply, liveText: StateFlow<LiveText>, list: LazyListState, reflow: AuroraHistoryReflow,
) {
    val current by rememberUpdatedState(reply)
    val id = reply.id
    LaunchedEffect(id, list, reflow, liveText) {
        snapshotFlow {
            val layout = list.layoutInfo
            val row = layout.visibleItemsInfo.firstOrNull { it.index == 0 && it.key == id }
            row?.let {
                val viewport = layout.viewportSize.height - layout.beforeContentPadding - layout.afterContentPadding
                Triple(it.size, viewport, current.hasPlacedAnswer(reflow))
            }
        }.collect { measured ->
            if (measured != null) {
                val live = liveText.value
                val hasAnswer = measured.third || (live.replyId == id && live.text.isNotBlank())
                val completedAnswer = measured.third && current.status == ReplyStatus.Completed
                reflow.tail.observeReplyHeight(id, measured.first, measured.second, hasAnswer, completedAnswer)
            }
        }
    }
}

private fun Reply.hasPlacedAnswer(reflow: AuroraHistoryReflow): Boolean =
    text.isNotBlank() && (historyContent?.plain != false || !reflow.isPreparingIncomingAnswer(id))

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
    val workMenu = if (message is Reply) remember(message.id) { AuroraWorkMenu() } else null
    val onMenuAction: (AiStudioAction) -> Unit = { action ->
        workMenu?.beforeDisclosure?.invoke(action)
        onAction(action)
    }
    val workAction = (message as? Reply)?.fullDisclosureAction(
        message.id in expandedWork, expandedSteps, expandedEvents, onMenuAction,
    )
    AuroraMessageMenu(
        message, touch, onMenuAction, readingWidth, selection, modifier.zIndex(if (workAccessVisible) 1f else 0f), liveText,
        workMenu = workMenu, workAction = workAction,
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
                workMenu = workMenu,
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
    val reveal = rememberArchiveReveal(reply, reflow, archive = true)
    Box(Modifier.widthIn(max = AuroraLayout.AgentBubble).focusGroup().auroraReplySurfaceArrival(touch).then(outline)
        .trackReplyHeight(reply, reflow, selection, reply.historyContent?.plain == false, reveal)) {
        AuroraReplyAnswer(
            reply, touch, onAction, null, selection, false, {}, reflow.viewportHeight, reveal,
            keepStartDuringResize = reflow.tail.keepsReplyStart(reply.id),
        )
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
    val desktopDeliveryError = !touch && user.delivery == Delivery.Failed
    val top = when {
        touch -> AuroraLayout.Space
        desktopDeliveryError -> AuroraLayout.DesktopCompactMessageTopInset
        else -> AuroraLayout.DesktopMessageVerticalInset
    }
    val bottom = when {
        touch -> null
        desktopDeliveryError -> AuroraLayout.DesktopCompactMessageBottomInset
        else -> AuroraLayout.DesktopMessageVerticalInset
    }
    AuroraMessageTimestamp(
        user.time,
        inlineAllowed = false,
        textSetsWidth = user.files.isEmpty() && textPresent,
        modifier = Modifier.widthIn(max = width).then(surface).padding(
            start = horizontalInset, end = horizontalInset, top = top,
        ),
        bottomPadding = bottom,
        status = { AuroraDeliveryStatus(user, touch) },
    ) { onTextLayout ->
        Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            user.files.forEach { file ->
                AuroraControl(
                    { onAction(AiStudioAction.OpenFile(file.openId)) }, touch = touch,
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
        Delivery.Failed -> if (touch) AuroraTouchDeliveryError(user.error) else AuroraDesktopDeliveryError(user.error)
        Delivery.Sending -> AuroraText("Отправляем…", small = true, color = AuroraColors.MessageMuted)
        Delivery.Sent -> Unit
    }
}

@Composable
private fun AuroraDesktopDeliveryError(error: String?) {
    val reason = error?.takeIf { it.isNotBlank() }
    val style = auroraTimestampStyle().copy(color = AuroraColors.Error)
    var focused by remember { mutableStateOf(false) }
    val focus = if (focused && HbFocusIndication.keyboardNavigation) {
        Modifier.border(AuroraLayout.Stroke, AuroraColors.Primary, RoundedCornerShape(AuroraLayout.SmallRadius))
    } else Modifier
    HbTooltip(reason.orEmpty(), enabled = reason != null) {
        Row(
            focus.onFocusChanged { focused = it.isFocused }.focusable(enabled = reason != null),
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Ошибка отправки", style = style)
            if (reason != null) BasicText("ⓘ", style = style)
        }
    }
}

@Composable
private fun AuroraTouchDeliveryError(error: String?) {
    AuroraMenu(listOf(HbMenuAction(error.orEmpty(), {}, enabled = false))) { trigger, open ->
        AuroraControl(
            open, trigger, label = "Причина ошибки отправки", touch = true,
            background = Color.Transparent, round = false,
        ) {
            AuroraText("Ошибка отправки  ⓘ", small = true, color = AuroraColors.Error)
        }
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
    workMenu: AuroraWorkMenu? = null,
) {
    val finished = reply.status != ReplyStatus.Working
    val hasWork = reply.steps.isNotEmpty()
    val focus = remember(reply.id) { AuroraWorkAccess(liveText != null) }
    val desktopHistory = hasWork && finished && !touch
    val workTitle = if (desktopHistory) auroraWorkTitle(reply.steps.size) else null
    val headerMinWidth = if (workTitle != null) auroraWorkHeaderMinWidth(workTitle) else Dp.Hairline
    val headerSelection = if (desktopHistory) rememberSelectionState() else null
    val scope = rememberCoroutineScope()
    val clearance = if (desktopHistory && reflow != null) {
        rememberAuroraWorkHeaderClearance(focus.headerHeight, expanded)
    } else null
    val onToggle = {
        focus.beforeToggle(reply.id, clearance, reflow, scope)
        onAction(AiStudioAction.ToggleWork(reply.id))
    }
    RestoreAuroraWorkFocus(focus, hasWork, expanded, finished)

    var accessFocused by remember { mutableStateOf(false) }
    var headerTextFocused by remember { mutableStateOf(false) }
    val visibleFocus = (accessFocused || headerTextFocused) && HbFocusIndication.keyboardNavigation
    val reveal = if (headerSelection != null) {
        val hovered by focus.hover.collectIsHoveredAsState()
        rememberAuroraWorkReveal(
            hovered,
            hoverOverMenu,
            expanded || visibleFocus,
            selection.selectedTexts.isNotEmpty() || headerSelection.selectedTexts.isNotEmpty(),
        )
            .takeIf { it.drawn }
    } else null
    val accessVisible = reveal != null
    val surface = focus.replySurface(
        expandedHistory = desktopHistory && expanded,
        headerOpacity = reveal?.let { header -> { header.opacity.value } },
    )
    val hoverTarget = if (desktopHistory) Modifier.hoverable(focus.hover) else Modifier
    val roomAbove = if (clearance != null) Modifier.auroraWorkHeaderClearance(clearance) else Modifier
    SideEffect {
        workMenu?.beforeDisclosure = { action ->
            when {
                action is AiStudioAction.ToggleWork ||
                    action is AiStudioAction.SetWorkExpanded && action.stepId == null && action.expanded != expanded ->
                    focus.beforeToggle(reply.id, clearance, reflow, scope)
                action is AiStudioAction.SetWorkExpanded ->
                    reflow?.beforeToggle(reply.id, focus.displayedHistoryHeight, focus.answerHeight.displayed, null, scope)
                else -> Unit
            }
        }
        onWorkAccessVisibility(accessVisible)
        focus.restoreEventAnchor(expandedEvents, reply.id, reflow, scope)
    }
    Box(focus.group(modifier.widthIn(max = AuroraLayout.AgentBubble).then(roomAbove)).then(hoverTarget)) {
        if (workTitle != null) {
            Box(Modifier.matchParentSize()) {
                AuroraWorkKeyboardAccess(
                    workTitle, expanded, focus.requester, { accessFocused = it }, onToggle,
                )
            }
        }
        AuroraAgentMessageBody(
            reply, expanded, touch, onAction, onToggle, liveText,
            selection, focus, reflow, expandedEvents,
            modifier = surface.widthIn(min = headerMinWidth),
        ) {
            AuroraWorkTimeline(
                reply, expandedSteps, expandedEvents, touch,
                onAction = { action ->
                    if (action is AiStudioAction.ToggleWorkEvent && reflow != null) {
                        focus.expectEventDisclosure(action, expandedEvents, reflow.tail.inputGeneration)
                    }
                    onAction(action)
                },
                onDisclosure = {
                    reflow?.beforeToggle(reply.id, focus.displayedHistoryHeight, focus.answerHeight.displayed, null, scope)
                },
                workMenu = workMenu.takeUnless { touch },
            )
        }
        if (reveal != null && headerSelection != null && workTitle != null) {
            Box(Modifier.matchParentSize()) {
                AuroraDesktopWorkHeader(
                    workTitle, expanded, visibleFocus, focus.hover, headerSelection, reveal, onToggle,
                    onTextFocus = { headerTextFocused = it },
                    modifier = Modifier.onSizeChanged { focus.headerHeight = it.height },
                    workMenu = workMenu,
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
    expandedEvents: Set<String>,
    modifier: Modifier = Modifier,
    work: @Composable () -> Unit,
) {
    val finished = reply.status != ReplyStatus.Working
    val finalResize = finished && focus.continuedFromStream
    val tracksHeight = liveText != null || finalResize || reply.historyContent?.plain == false
    val reveal = rememberArchiveReveal(reply, reflow, archive = false)
    val surface = Modifier.auroraReplySurfaceArrival(touch)
        .then(modifier)
        .trackReplyHeight(reply, reflow, selection, tracksHeight, reveal)
    val scope = rememberCoroutineScope()
    val onResizeEnd = { if (finalResize) focus.finishStreamResize() }
    val expandedThinking = !finished && reply.steps.any { step ->
        step.events.any { it.kind == WorkEventKind.Thinking && "${reply.id}/${step.id}/${it.id}" in expandedEvents }
    }
    AuroraReplyLayout(
        history = {
            AuroraReplyWork(reply, expanded, touch, onToggle, focus, reflow, finalResize, onResizeEnd, expandedThinking, work)
        },
        answer = {
            AuroraReplyAnswer(
                reply, touch, onAction, liveText, selection, finalResize, onResizeEnd,
                viewportHeight = reflow?.viewportHeight ?: Dp.Hairline,
                archiveReveal = reveal,
                keepStartDuringResize = reflow?.tail?.keepsReplyStart(reply.id) == true,
                anchoredHeight = focus.answerHeight.takeIf { !touch && reply.steps.isNotEmpty() && reflow != null },
                onAnchoredHeight = { height, target -> reflow?.answerResized(reply.id, height, target, scope) },
            )
        },
        modifier = surface,
        expandToFitHistory = (expanded || !finished) && (reply.steps.isNotEmpty() || reply.interim != null),
    )
}

@Composable
private fun rememberArchiveReveal(reply: Reply, reflow: AuroraHistoryReflow?, archive: Boolean): ArchiveReveal? {
    if (reply.historyContent?.plain != false || !supportsAuroraStagedRichLayout()) return null
    val offset = reflow?.openingOffset(reply.id) ?: 0
    val incoming = remember(reply.id) { reflow?.isIncomingReply(reply.id) == true }
    if (!archive && offset <= 0 && !incoming) return null
    return remember(reply.id) { ArchiveReveal(offset, reserveSpace = !incoming) { reflow?.finishOpening(reply.id) } }
}

@Composable
private fun Modifier.trackReplyHeight(
    reply: Reply,
    reflow: AuroraHistoryReflow?,
    selection: SelectionState,
    tracksHeight: Boolean,
    reveal: ArchiveReveal?,
): Modifier {
    val scope = rememberCoroutineScope()
    val tracker = remember(reply.id) { ReplyHeightTracker() }
    return layout { measurable, constraints ->
        // Final Markdown preparation can finish after the stream's first completed frame.
        tracker.tracked = tracker.tracked || tracksHeight
        Snapshot.withoutReadObservation {
            tracker.before = reflow?.tail?.beforeResize()
            // A child can finish opening its archive or settle a disclosure during this measure.
            val restoring = reveal != null && reveal.initialOffset > 0 && !reveal.isPlaced
            tracker.anchored = restoring || reflow?.ownsWorkResize(reply.id) == true
        }
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }.onSizeChanged { size ->
        val previous = tracker.height
        tracker.height = size.height
        if (tracker.anchored) return@onSizeChanged
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
    var anchored = false
    var before: AuroraTailFollow.BeforeResize? = null
}

@Composable
private fun AuroraWorkAccess.replySurface(expandedHistory: Boolean, headerOpacity: (() -> Float)?): Modifier {
    if (expandedHistory) {
        return Modifier.auroraExpandedWorkSurface(headerHeight)
    }
    val radius = AuroraLayout.MessageRadius
    return if (headerOpacity == null) {
        HbAuroraOutline(AuroraColors.AgentMessage, AuroraColors.MessageOutline, radius)
    } else {
        HbAuroraOutline.joiningHeader(AuroraColors.AgentMessage, AuroraColors.MessageOutline, radius, headerOpacity)
    }
}
