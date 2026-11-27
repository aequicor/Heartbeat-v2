@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.projects_sessions
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraWorkspace(
    state: AiStudioScreenState,
    draft: StateFlow<Draft>,
    liveText: StateFlow<LiveText>,
    backdrop: AuroraBackdrop?,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    showNavigation: Boolean,
    modifier: Modifier = Modifier,
    captionOverlap: Dp = Dp.Hairline,
    caption: HbWindowCaption = HbWindowCaption.current,
    castsSidebarShadow: Boolean = false,
    startPaneSplitterOverlap: Dp = Dp.Hairline,
) {
    val chatId = state.chat?.id
    var preferredWidth by rememberSaveable { mutableStateOf(AuroraLayout.Header.value) }
    var readingResizePointerHeld by remember { mutableStateOf(false) }
    val onReadingResizePointerHeldChange = remember { { held: Boolean -> readingResizePointerHeld = held } }
    // The chat key recreates the header, so the requested focus target lives in the workspace.
    var subagentFocusTarget by remember { mutableStateOf<String?>(null) }
    val clearance = auroraCaptionClearance(captionOverlap)
    val frame = if (touch) {
        Modifier.auroraWindowFrame(clearance, caption.height, caption.leftInset > Dp.Hairline)
    } else {
        val shape = RoundedCornerShape(
            topStart = AuroraLayout.ChatSurfaceRadius,
            bottomStart = AuroraLayout.ChatSurfaceRadius,
        )
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1 else 1
        val shadow = if (castsSidebarShadow) {
            Modifier.dropShadow(
                shape,
                Shadow(
                    radius = AuroraLayout.ChatPanelShadowRadius,
                    color = AuroraColors.ChatPanelShadow,
                    offset = DpOffset(AuroraLayout.ChatPanelShadowOffset * direction, Dp.Hairline),
                ),
            ).dropShadow(
                shape,
                Shadow(
                    radius = AuroraLayout.ChatPanelContactRadius,
                    color = AuroraColors.ChatPanelContactShadow,
                    offset = DpOffset(AuroraLayout.Stroke * direction, Dp.Hairline),
                ),
            )
        } else Modifier
        shadow.clip(shape).auroraHeldResizeCursor(readingResizePointerHeld)
    }
    val floatingBackdrop = if (touch) null else rememberAuroraFloatingBackdrop()
    val surface = modifier.then(frame)
    Box(if (touch) surface.auroraBackdrop(backdrop) else surface) {
        CompositionLocalProvider(
            LocalAuroraFloatingBackdrop provides floatingBackdrop,
            LocalAuroraReadingResizePointerHeldChange provides onReadingResizePointerHeldChange,
        ) {
            if (floatingBackdrop != null) {
                Box(Modifier.matchParentSize().then(floatingBackdrop.recordWallpaper()).auroraBackdrop(backdrop))
            }
            val bootstrap = state.sessionBootstrap
            if (bootstrap != null) {
                key(bootstrap.id) {
                    AuroraBootstrapWorkspace(
                        state, bootstrap, touch, showNavigation, Dp(preferredWidth), clearance, caption, onAction,
                    )
                }
            } else if (chatId != null) {
                AuroraChatHost(chatId) {
                    val chatAction: (AiStudioAction) -> Unit = { action ->
                        onAction(
                            when (action) {
                                AiStudioAction.Send -> AiStudioAction.SendChatDraft(chatId)
                                AiStudioAction.CancelTurn -> AiStudioAction.CancelChatTurn(chatId)
                                is AiStudioAction.AnswerPermission -> action.copy(chatId = chatId)
                                else -> action
                            },
                        )
                    }
                    AuroraChatViewport(
                        state, draft, liveText, touch, chatAction, showNavigation, Dp(preferredWidth),
                        onWidthChange = { preferredWidth = it.value },
                        startPaneSplitterOverlap = startPaneSplitterOverlap,
                        captionClearance = clearance,
                        caption = caption,
                        onHeaderAction = { action ->
                            if (!touch && action is AiStudioAction.OpenChat) subagentFocusTarget = action.id
                            onAction(action)
                        },
                        onDesktopTitleReady = { focus ->
                            if (subagentFocusTarget == chatId) focus.requestFocus()
                            subagentFocusTarget = null
                        },
                    )
                }
            } else if (state.loading) {
                AuroraText("Загрузка истории…", Modifier.align(Alignment.Center))
            } else {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space)) {
                    if (showNavigation) {
                        AuroraChip(
                            stringResource(Res.string.projects_sessions), { onAction(AiStudioAction.ExpandChats) },
                            touch = touch,
                        )
                    }
                    AuroraChip(
                        stringResource(Res.string.new_session), { onAction(AiStudioAction.CreateSessionWithoutProject) },
                        touch = touch,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraChatViewport(
    state: AiStudioScreenState,
    draft: StateFlow<Draft>,
    liveText: StateFlow<LiveText>,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    showNavigation: Boolean,
    preferredWidth: Dp,
    onWidthChange: (Dp) -> Unit,
    startPaneSplitterOverlap: Dp,
    onHeaderAction: (AiStudioAction) -> Unit,
    onDesktopTitleReady: (FocusRequester) -> Unit,
    captionClearance: Dp,
    caption: HbWindowCaption,
) {
    val chatId = requireNotNull(state.chat).id
    val density = LocalDensity.current
    val restoredOffset = if (state.restoreAnchor.messageId == null) 0 else state.restoreAnchor.offset
    val list = rememberLazyListState(state.restoreIndex, restoredOffset)
    val tailFollow = remember(list) { AuroraTailFollow(list) }
    val documents = remember(list) { AuroraCompletedDocuments() }
    val historyReady = rememberAuroraInitialHistory(state, documents)
    var topPx by remember { mutableIntStateOf(0) }
    var lowerPanelPx by remember { mutableIntStateOf(0) }
    val hasMessages = state.messages.isNotEmpty() || state.live != null
    val awayFromTail by remember(list) { derivedStateOf { list.canScrollBackward } }
    val bottomEdgePx = with(density) { AuroraLayout.DesktopComposerBottom.roundToPx() }
    val sendFollowDistance = with(density) { AuroraLayout.SendFollowDistance.roundToPx() }

    TrackAuroraReading(chatId, state, list, tailFollow, onAction)

    val latestAction = rememberUpdatedState(onAction)
    val referenceInteraction = remember(chatId) { AuroraReferenceInteraction(chatId) { latestAction.value(it) } }
    CompositionLocalProvider(LocalAuroraReferenceInteraction provides referenceInteraction) {
        BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && referenceInteraction.hasAnySelection()) {
                referenceInteraction.clearSelection()
                true
            } else false
        }) {
            val corridor = preferredWidth.coerceAtMost(maxWidth)
            val gutter = (maxWidth - corridor) / 2f
            val headerInset = (AuroraLayout.ContentInset - gutter).coerceAtLeast(Dp.Hairline)
            val viewportWidth = maxWidth
            val viewportHeight = maxHeight
            AuroraChatLayers(panelHeight = { lowerPanelPx }, controls = {
                AuroraPlaceLowerControls(list, touch, viewportWidth, corridor, awayFromTail && hasMessages, state.request != null,
                    onStop = { onAction(AiStudioAction.CancelTurn) },
                    onTail = tailFollow::returnToTail,
                ) { inlineTail, inlineStop ->
                    AuroraLowerControls(
                        state, draft, list, inlineTail, touch, { action ->
                            if (action == AiStudioAction.Send) tailFollow.followOnSend(sendFollowDistance)
                            onAction(action)
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
                            .auroraLowerLayout(touch, corridor),
                        maxPanelHeight = viewportHeight * 0.76f, maxReferenceHeight = viewportHeight * 0.45f, inlineRequestStop = inlineStop,
                        onTail = tailFollow::returnToTail,
                        onPanelHeight = { next ->
                            if (!touch) list.preserveReadingOnComposerResize(lowerPanelPx, next)
                            lowerPanelPx = next
                        },
                    )
                }
            }) { panelHeight ->
                val historyPadding = with(density) { auroraHistoryPadding(touch, topPx.toDp(), panelHeight.toDp()) }
                if (hasMessages && historyReady && topPx > 0 && panelHeight > 0) {
                    val historyWithEdges = Modifier.align(Alignment.Center).fillMaxSize().auroraHistoryEdges(
                        topPx,
                        bottomHeightPx = if (!touch && awayFromTail) panelHeight + bottomEdgePx else 0,
                        readingWidth = corridor,
                    )
                    AuroraHistory(
                        state, liveText, list, touch, onAction,
                        historyPadding,
                        historyWithEdges,
                        readingWidth = corridor, viewportHeight = viewportHeight, tailFollow = tailFollow, preparedHistory = documents,
                    )
                    val trackPadding = auroraTrackPadding(touch, historyPadding, captionClearance, caption)
                    AuroraChatScrollbar(list, touch, trackPadding, tailFollow::userScroll)
                }
                AuroraResizeHandles(touch, viewportWidth, corridor, gutter, historyPadding, startPaneSplitterOverlap, onWidthChange)
            }
            AuroraHeader(
                state, touch, showNavigation, onHeaderAction,
                Modifier.align(Alignment.TopCenter).zIndex(1f).widthIn(max = corridor)
                    .onSizeChanged { topPx = it.height },
                captionClearance = headerCaptionClearance(captionClearance, gutter + headerInset),
                captionOnLeft = caption.leftInset > Dp.Hairline,
                edgeInset = headerInset,
                onDesktopTitleReady = onDesktopTitleReady,
            )
        }
    }
}

/** Measure the composer before the reverse list so its padding and reading anchor change in the same frame. */
@Composable
private fun AuroraChatLayers(
    panelHeight: () -> Int,
    controls: @Composable BoxScope.() -> Unit,
    history: @Composable BoxScope.(Int) -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val panel = subcompose("controls") { Box(Modifier.fillMaxWidth(), content = controls) }
            .single().measure(constraints.copy(minHeight = 0))
        val measuredHeight = panelHeight()
        val messages = subcompose("history") { Box(Modifier.fillMaxSize()) { history(measuredHeight) } }
            .single().measure(constraints)
        layout(messages.width, messages.height) {
            messages.placeRelative(0, 0)
            panel.placeRelative(0, messages.height - panel.height)
        }
    }
}

@Composable
private fun rememberAuroraInitialHistory(state: AiStudioScreenState, documents: AuroraCompletedDocuments): Boolean {
    val hasHistory = state.messages.isNotEmpty()
    val initialReplies = remember(documents, hasHistory) {
        val newer = if (state.live == null) 1 else 2
        val start = (state.restoreIndex - newer).coerceAtLeast(0)
        state.messages.asSequence().drop(start).take(MaxCachedReplies)
            .filterIsInstance<AiStudioScreenState.Reply>()
            .filter { it.text.length <= MaxCachedReplyLength }
            .mapNotNull { it.historyContent?.takeUnless { content -> content.plain } }
            .toList()
    }
    val monospace = auroraCodeFontFamily()
    var ready by remember(initialReplies, monospace) { mutableStateOf(initialReplies.isEmpty()) }
    if (!ready) LaunchedEffect(documents, initialReplies, monospace) {
        documents.prepareInitial(initialReplies, monospace)
        ready = true
    }
    return ready
}

private fun auroraTrackPadding(
    touch: Boolean,
    historyPadding: PaddingValues,
    captionClearance: Dp,
    caption: HbWindowCaption,
): PaddingValues {
    if (touch) return historyPadding
    return if (caption.leftInset > Dp.Hairline || captionClearance <= Dp.Hairline) {
        PaddingValues(vertical = AuroraLayout.FrameRadius)
    } else {
        val corner = AuroraLayout.WindowFrameRadius - AuroraLayout.FrameStroke
        PaddingValues(top = caption.height + AuroraLayout.WindowJoinBelowCaption + corner, bottom = corner)
    }
}

private fun auroraHistoryPadding(touch: Boolean, header: Dp, lower: Dp): PaddingValues = PaddingValues(
    top = header + if (touch) AuroraLayout.Space else AuroraLayout.ContentInset,
    // The floating jump-to-latest button can appear while scrolling without resizing the history.
    bottom = lower + if (touch) {
        AuroraLayout.ChromeInset * 2 + AuroraLayout.ContentInset
    } else {
        AuroraLayout.DesktopComposerBottom + AuroraLayout.ComposerHistoryGap
    },
)

@Composable
private fun BoxScope.AuroraPlaceLowerControls(
    list: LazyListState,
    touch: Boolean,
    viewportWidth: Dp,
    corridor: Dp,
    showTail: Boolean,
    hasRequest: Boolean,
    onStop: () -> Unit,
    onTail: () -> Unit,
    content: @Composable (inlineTail: Boolean, inlineStop: Boolean) -> Unit,
) {
    val inset = AuroraLayout.ContentInset + AuroraLayout.Space
    val sideSpace = (viewportWidth - corridor) / 2f
    val fitsSide = !touch && sideSpace >= AuroraLayout.DesktopTarget + inset * 2f
    content(showTail && !fitsSide, !fitsSide)
    val bottom = AuroraLayout.DesktopComposerBottom + AuroraLayout.SmallGap
    if (showTail && fitsSide) {
        AuroraTailControl(
            list, touch,
            Modifier.align(Alignment.BottomEnd).zIndex(1f)
                .padding(end = inset, bottom = bottom),
            onJump = onTail,
        )
    }
    if (hasRequest && fitsSide) {
        val aboveTail = if (showTail) AuroraLayout.DesktopTarget + AuroraLayout.SmallGap else Dp.Hairline
        AuroraRequestStop(
            onStop, touch,
            Modifier.align(Alignment.BottomEnd).zIndex(1f).padding(end = inset, bottom = bottom + aboveTail),
            floating = true,
        )
    }
}

@Composable
private fun BoxScope.AuroraResizeHandles(
    touch: Boolean,
    viewportWidth: Dp,
    corridor: Dp,
    gutter: Dp,
    historyPadding: PaddingValues,
    startPaneSplitterOverlap: Dp,
    onWidthChange: (Dp) -> Unit,
) {
    if (touch || viewportWidth <= AuroraLayout.ReadingMinWidth) return
    val resize: (Dp) -> Unit = { requested ->
        onWidthChange(requested.coerceIn(AuroraLayout.ReadingMinWidth, viewportWidth))
    }
    val reset = { onWidthChange(AuroraLayout.Header) }
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val offset = gutter - AuroraLayout.Space
    val maxOffset = viewportWidth - AuroraLayout.Space
    val fromStart = offset.coerceIn(startPaneSplitterOverlap, maxOffset)
    val fromEnd = offset.coerceIn(AuroraReadingScrollbarGutter, maxOffset)
    AuroraResizeHandle(
        corridor, -towardsEnd, resize, reset,
        hitAreaPadding = historyPadding,
        modifier = Modifier.align(Alignment.CenterStart).offset(x = fromStart),
    )
    AuroraResizeHandle(
        corridor, towardsEnd, resize, reset,
        hitAreaPadding = historyPadding,
        modifier = Modifier.align(Alignment.CenterEnd).offset(x = -fromEnd),
    )
}

internal fun Modifier.auroraLowerLayout(touch: Boolean, corridor: Dp): Modifier =
    widthIn(max = corridor).padding(
        start = if (touch) AuroraLayout.ContentInset else Dp.Hairline,
        end = if (touch) AuroraLayout.ContentInset else Dp.Hairline,
        top = if (touch) AuroraLayout.ChromeInset else Dp.Hairline,
        bottom = if (touch) AuroraLayout.ChromeInset else AuroraLayout.DesktopComposerBottom,
    )

internal fun LazyListState.preserveReadingOnComposerResize(previousHeight: Int, nextHeight: Int) {
    if (previousHeight <= 0 || nextHeight == previousHeight) return
    if (canScrollBackward && !isScrollInProgress) {
        requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset + nextHeight - previousHeight)
    }
}

internal fun headerCaptionClearance(caption: Dp, sides: Dp): Dp =
    if (caption > Dp.Hairline) {
        (caption + AuroraLayout.HeaderCaptionGap - sides - AuroraLayout.FrameStroke).coerceAtLeast(Dp.Hairline)
    } else Dp.Hairline

@Composable
private fun TrackAuroraReading(
    chatId: String,
    state: AiStudioScreenState,
    list: LazyListState,
    tail: AuroraTailFollow,
    onAction: (AiStudioAction) -> Unit,
) {
    val dispatchReading by rememberUpdatedState(onAction)
    val before = state.olderMessagesBefore
    if (before != null) {
        LaunchedEffect(chatId, list, before) {
            snapshotFlow {
                val layout = list.layoutInfo
                layout.visibleItemsInfo.any { it.index >= layout.totalItemsCount - 3 }
            }.collect { nearStart ->
                if (nearStart) dispatchReading(AiStudioAction.LoadOlderMessages(chatId, before))
            }
        }
    }
    val newestId = state.live?.id ?: state.messages.firstOrNull()?.id
    SideEffect(newestId, list) {
        if (tail.following && newestId != null) tail.returnToTail()
    }
    LaunchedEffect(chatId, list) {
        snapshotFlow {
            val index = list.firstVisibleItemIndex
            val offset = list.firstVisibleItemScrollOffset
            val layout = list.layoutInfo
            when {
                layout.totalItemsCount == 0 -> null
                index == 0 && offset == 0 -> Anchor()
                else -> (layout.visibleItemsInfo.firstOrNull { it.index == index }?.key as? String)
                    ?.let { Anchor(it, offset) }
            }
        }.collect { anchor ->
            if (anchor != null) {
                dispatchReading(AiStudioAction.ReadAt(chatId, anchor))
            }
        }
    }
}
