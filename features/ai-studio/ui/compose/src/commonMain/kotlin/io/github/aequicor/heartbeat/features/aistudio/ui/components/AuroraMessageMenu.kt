package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.LiveText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuMotion
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraReferenceSelection
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_reference_copy
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_reference_quote
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_reference_reply
import kotlin.math.roundToInt

@Composable
internal fun AuroraMessageMenu(
    message: Message,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    readingWidth: Dp,
    maxColumnWidth: Dp,
    selection: SelectionState,
    modifier: Modifier = Modifier,
    liveText: StateFlow<LiveText>? = null,
    workMenu: AuroraWorkMenu? = null,
    workAction: HbMenuAction? = null,
    content: @Composable (hoverOverMenu: Boolean) -> Unit,
) {
    val user = message is User
    val hitArea = remember { MessageMenuHitArea() }
    val menu = remember { MessageMenuState() }
    val expanded = menu.expanded
    val onContextMenu by rememberUpdatedState(onAction)
    val references = LocalAuroraReferenceInteraction.current
    val referenceRow = LocalAuroraReferenceRow.current
    val replyAction = if (message is User && message.delivery == Delivery.Sent) {
        references?.replyAction(AuroraReferenceSelection(message.id, AuroraReferenceSelection.Kind.Message,
            revisionId = message.revisionId))
    } else null
    val actions = listOfNotNull(replyAction) + auroraMessageActions(message, touch, onAction, liveText) +
        listOfNotNull(workAction)
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val hasContext = !touch && (actions.isNotEmpty() || workMenu != null || referenceRow != null)
    val hasFileMenu = !touch && when (message) {
        is User -> message.files.isNotEmpty()
        is Reply -> message.result != null || message.artifacts.isNotEmpty() ||
            message.steps.any { step -> step.events.any { it.files.isNotEmpty() } }
    }
    val openContext = remember(hitArea, menu, workMenu, hasContext, referenceRow, references, clipboard, message, liveText) {
        if (hasContext) { root: Offset ->
            val anchor = hitArea.anchorAt(root - hitArea.rootOrigin)
            val region = referenceRow?.regionAt(root)
            val selected = referenceRow?.selected(region)
            val origin = region?.source ?: referenceRow?.source
            val referenceActions = if (references != null && origin != null) buildList<HbMenuAction> {
                add(references.replyAction(origin))
                if (references.hasSelection(selected)) {
                    add(HbMenuAction("Цитировать", { references.quote(selected, region?.selection ?: selection) },
                        leadingIcon = { AuroraReferenceIcon(Res.drawable.chat_reference_quote) }))
                    add(HbMenuAction("Копировать выделенное", {
                        clipboard.setText(AnnotatedString(selected?.selectedText ?: references.copiedSelection()))
                    }, leadingIcon = { AuroraReferenceIcon(Res.drawable.chat_reference_copy) }))
                } else {
                    val text = region?.rawText?.takeIf(String::isNotBlank) ?: when (message) {
                        is User -> message.text
                        is Reply -> liveText?.value?.textFor(message.id) ?: message.text.ifBlank { message.interim.orEmpty() }
                    }
                    add(HbMenuAction("Копировать текст", { clipboard.setText(AnnotatedString(text.referencePreviewText())) },
                        enabled = text.isNotEmpty(), leadingIcon = { AuroraReferenceIcon(Res.drawable.chat_reference_copy) }))
                }
            } else null
            menu.showAtPointer(anchor, referenceActions ?: listOfNotNull(workMenu?.actionAt(root, onContextMenu)),
                exclusive = referenceActions != null)
        } else null
    }
    SideEffect { workMenu?.openAt = openContext }
    val touchAtEdge = touch && !hitArea.outsideFits
    SideEffect(expanded) {
        if (expanded && touchAtEdge) selection.clear()
    }
    AuroraMessageMenuLayout(
        user, touch, readingWidth, maxColumnWidth, hitArea,
        bubble = {
            val atEdge = if (touchAtEdge && actions.isNotEmpty()) Modifier.openMenuOnHold(menu.open)
            else Modifier
            Box(atEdge) {
                val menuHovered = (hitArea.hoveredOutsideBubble || expanded) && actions.isNotEmpty()
                if (hasFileMenu) {
                    CompositionLocalProvider(LocalAttachmentContextMenuOpened provides hitArea.attachmentMenuOpened) {
                        content(menuHovered)
                    }
                } else {
                    content(menuHovered)
                }
            }
        },
        menu = {
            val menuActions = menu.contextActions + if (menu.exclusive) emptyList() else actions
            if (menuActions.isNotEmpty()) {
                HbMenu(
                    expanded, menu.close, menuActions,
                    presentation = if (touch) {
                        HbMenuPresentation.AuroraTouchMessage
                    } else HbMenuPresentation.AuroraDesktopMessageActions,
                    alignEnd = user,
                    restoreTriggerFocus = !touchAtEdge && menu.pointerAnchor == null,
                    pointerAnchor = menu.pointerAnchor,
                    popupWidth = 224.dp.takeIf { menu.exclusive },
                    popupBackground = if (menu.exclusive) {
                        { shape -> Modifier.background(AuroraColors.AgentMessage, shape) }
                    } else null,
                    motion = HbMenuMotion(
                        replaceTrigger = menu.pointerAnchor == null,
                        hoverMenu = menu.openedByHover,
                        onHover = menu.onPopupHover,
                    ).takeUnless { touch },
                ) { trigger ->
                    val toggle = if (expanded) menu.close else menu.open
                    AuroraMessageMenuTrigger(hitArea, expanded, touch, toggle, menu.open, trigger)
                }
            }
        },
        footer = {
            if (message.time.isNotBlank()) {
                val color = if (user) AuroraColors.User else AuroraColors.AgentMessage
                AuroraTimestampText(
                    message.time,
                    Modifier.background(color, RoundedCornerShape(AuroraLayout.SmallRadius))
                        .padding(horizontal = AuroraLayout.Space, vertical = AuroraLayout.FrameStroke),
                )
            }
        },
        modifier = modifier.onGloballyPositioned { hitArea.rootOrigin = it.positionInRoot() }
            .trackMessageMenuPointer(
                hitArea, openContext, menu.openOnHover.takeIf { !touch && actions.isNotEmpty() }, hasFileMenu,
                references.takeUnless { touch }, referenceRow.takeUnless { touch },
            ),
    )
}

private fun AuroraReferenceInteraction.replyAction(source: AuroraReferenceSelection): HbMenuAction =
    HbMenuAction("Ответить", { reply(source.replySource()) },
        leadingIcon = { AuroraReferenceIcon(Res.drawable.chat_reference_reply) })

private class MessageMenuState {
    var expanded by mutableStateOf(false)
        private set
    var pointerAnchor by mutableStateOf<IntOffset?>(null)
        private set
    var openedByHover by mutableStateOf(false)
        private set
    var exclusive by mutableStateOf(false)
        private set
    var contextActions by mutableStateOf(emptyList<HbMenuAction>())
        private set
    private var enteredHoverMenu = false
    val open: () -> Unit = { show(null) }
    val openOnHover: () -> Unit = { show(null, byHover = true) }
    val close: () -> Unit = { expanded = false }
    val onPopupHover: (Boolean) -> Unit = { hovered ->
        if (hovered) enteredHoverMenu = true
        else if (expanded && openedByHover && enteredHoverMenu) close()
    }

    fun showAtPointer(anchor: IntOffset, actions: List<HbMenuAction>, exclusive: Boolean) =
        show(anchor, context = actions, exclusive = exclusive)

    private fun show(
        anchor: IntOffset?, byHover: Boolean = false, context: List<HbMenuAction> = emptyList(), exclusive: Boolean = false,
    ) {
        this.exclusive = exclusive
        pointerAnchor = anchor
        openedByHover = byHover
        contextActions = context
        enteredHoverMenu = false
        expanded = true
    }
}

@Composable
private fun AuroraMessageMenuLayout(
    user: Boolean,
    touch: Boolean,
    readingWidth: Dp,
    maxColumnWidth: Dp,
    hitArea: MessageMenuHitArea,
    bubble: @Composable () -> Unit,
    menu: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val externalTimestamp = remember { mutableStateOf(false) }
    val bodyWithTimestamp: @Composable () -> Unit = {
        CompositionLocalProvider(LocalAuroraExternalTimestamp provides externalTimestamp, content = bubble)
    }
    Layout(contents = listOf(bodyWithTimestamp, menu, footer), modifier = modifier.fillMaxWidth()) { slots, constraints ->
        val (bodySlot, menuSlot, footerSlot) = slots
        val width = constraints.maxWidth
        val reading = if (readingWidth.value.isFinite()) minOf(width, readingWidth.roundToPx()) else width
        val gap = AuroraLayout.SmallGap.roundToPx()
        val wideEdge = AuroraLayout.FrameRadius.roundToPx() + gap
        val edge = if (reading >= AuroraLayout.Transcript.roundToPx() + wideEdge * 2) wideEdge
        else AuroraLayout.PaneGap.roundToPx()
        val resizeInset = if (touch || width <= AuroraLayout.ReadingMinWidth.roundToPx()) 0 else {
            (AuroraReadingScrollbarGutter + AuroraLayout.Space).roundToPx() - (width - reading) / 2
        }
        val column = (reading - maxOf(edge, resizeInset) * 2).coerceIn(0, maxColumnWidth.roundToPx())
        val left = (width - column) / 2
        val right = left + column
        val body = bodySlot.single().measure(constraints.copy(minWidth = 0, maxWidth = column))
        val stamp = if (externalTimestamp.value) {
            footerSlot.singleOrNull()?.measure(Constraints(maxWidth = column))
        } else null

        val target = if (touch) AuroraLayout.TouchTarget.roundToPx() else AuroraLayout.DesktopTarget.roundToPx()
        val trigger = menuSlot.singleOrNull()?.measure(Constraints.fixed(target, target))
        val bodyX = if (user) right - body.width else left
        val bounds = messageMenuAnchor(user, touch, width, bodyX, body.width, body.height, target)
        val mirror = layoutDirection == LayoutDirection.Rtl
        hitArea.place(
            messageMenuBounds(width, bodyX, 0, body.width, body.height, mirror),
            messageMenuBounds(width, bounds.x, bounds.y, target, target, mirror),
            bounds.available,
        )
        val extra = stamp?.let { gap + it.height } ?: 0
        layout(width, body.height + extra) {
            body.placeRelative(bodyX, 0)
            trigger?.placeRelative(bounds.x, bounds.y)
            if (stamp != null) {
                val stampX = if (user) right - stamp.width else left
                stamp.placeRelative(stampX, body.height + gap)
            }
        }
    }
}

@Composable
private fun AuroraMessageMenuTrigger(
    hitArea: MessageMenuHitArea,
    expanded: Boolean,
    touch: Boolean,
    toggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = remember { MutableInteractionSource() }
    val focused by keyboard.collectIsFocusedAsState()
    val focusVisible = focused && HbFocusIndication.keyboardNavigation
    val outside = hitArea.outsideFits
    val desktopHover = outside && !touch
    val reveal = rememberMessageActionBadge(
        hovered = desktopHover && hitArea.hovered,
        overBubble = desktopHover && hitArea.overMessage,
        pinned = focusVisible || outside && touch,
        expanded = expanded,
        touch = touch,
    )
    SideEffect {
        hitArea.opensOnBadgeEnter = desktopHover && reveal.interactive && !expanded
    }
    val pointer = if (reveal.interactive) {
        Modifier.pointerInput(toggle) {
            detectTapGestures(onTap = { toggle() })
        }
    } else Modifier
    Box(
        modifier.fillMaxSize().then(pointer).messageMenuButton(keyboard, onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (reveal.drawn) {
            Box(Modifier.graphicsLayer { alpha = reveal.opacity.value }) {
                AuroraMessageMenuGlyph(touch, focusVisible)
            }
        }
    }
}

private fun Modifier.messageMenuButton(keyboard: MutableInteractionSource, onOpen: () -> Unit): Modifier =
    semantics {
        role = Role.Button
        contentDescription = MessageActionsLabel
        onClick { onOpen(); true }
    }.onKeyEvent { event ->
        when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                if (event.type == KeyEventType.KeyUp) onOpen()
                true
            }
            else -> false
        }
    }.focusable(interactionSource = keyboard)

@Composable
private fun AuroraMessageMenuGlyph(touch: Boolean, focusVisible: Boolean) {
    if (touch) {
        HbIcon(HbIconGlyph.More, tint = AuroraColors.Muted, size = AuroraLayout.MenuGlyph)
        return
    }
    val focus = if (focusVisible) {
        Modifier.border(AuroraLayout.FrameStroke, AuroraColors.MessageInk, CircleShape)
    } else Modifier
    Box(
        Modifier.size(AuroraLayout.MessageMenuBadge)
            .background(AuroraColors.MessageActionBadge, CircleShape).then(focus),
        contentAlignment = Alignment.Center,
    ) {
        HbIcon(HbIconGlyph.ChevronDown, tint = AuroraColors.MessageInk, size = AuroraLayout.MessageMenuChevron)
    }
}

private data class MessageMenuAnchor(val x: Int, val y: Int, val available: Boolean)

private fun MeasureScope.messageMenuAnchor(
    user: Boolean, touch: Boolean, width: Int, bodyX: Int, bodyWidth: Int, bodyHeight: Int, target: Int,
): MessageMenuAnchor {
    val bodyRight = bodyX + bodyWidth
    val offset = AuroraLayout.Space.roundToPx()
    if (touch) {
        val gap = AuroraLayout.SmallGap.roundToPx()
        val x = if (user) bodyRight + gap else bodyX - gap - target
        val limit = (width - target).coerceAtLeast(0)
        return MessageMenuAnchor(x.coerceIn(0, limit), (bodyHeight - target - offset).coerceAtLeast(0), x in 0..limit)
    }
    val radius = AuroraLayout.MessageMenuBadge.roundToPx() / 2
    val center = if (user) bodyX - offset else bodyRight + offset
    val x = center.coerceIn(radius, (width - radius).coerceAtLeast(radius)) - target / 2
    val bottomInset = maxOf(offset, radius)
    val y = (bodyHeight - bottomInset - target / 2).coerceAtLeast(0)
    return MessageMenuAnchor(x, y, width >= radius * 2)
}

private class MessageMenuHitArea {
    private var messageBounds = Rect.Zero
    private var connectedBounds = Rect.Zero
    private var triggerBounds = Rect.Zero
    private var overBadge = false
    var attachmentMenuHandled = false
    val attachmentMenuOpened: () -> Unit = { attachmentMenuHandled = true }
    var rootOrigin = Offset.Zero
    var opensOnBadgeEnter = false
    var outsideFits by mutableStateOf(false)
        private set
    var hovered by mutableStateOf(false)
        private set
    var overMessage by mutableStateOf(false)
        private set
    val hoveredOutsideBubble get() = hovered && !overMessage

    fun place(message: Rect, trigger: Rect, outside: Boolean) {
        messageBounds = message
        triggerBounds = trigger
        connectedBounds = Rect(
            left = minOf(message.left, trigger.left),
            top = trigger.top,
            right = maxOf(message.right, trigger.right),
            bottom = trigger.bottom,
        )
        outsideFits = outside
    }

    fun move(point: Offset): Boolean {
        overMessage = messageBounds.contains(point)
        hovered = overMessage || outsideFits && connectedBounds.contains(point)
        val wasOverBadge = overBadge
        overBadge = triggerBounds.contains(point)
        return overBadge && !wasOverBadge
    }

    fun leave(point: Offset) {
        overMessage = false
        hovered = false
        // A modal popup can send an exit without moving the pointer off the badge.
        if (!triggerBounds.contains(point)) overBadge = false
    }

    fun containsMessage(point: Offset): Boolean = messageBounds.contains(point)

    fun anchorAt(point: Offset): IntOffset {
        val relative = point - triggerBounds.topLeft
        return IntOffset(relative.x.roundToInt(), relative.y.roundToInt())
    }
}

private fun messageMenuBounds(
    parentWidth: Int, x: Int, y: Int, width: Int, height: Int, mirror: Boolean,
): Rect {
    val left = (if (mirror) parentWidth - x - width else x).toFloat()
    return Rect(left, y.toFloat(), left + width, (y + height).toFloat())
}

private fun Modifier.trackMessageMenuPointer(
    hitArea: MessageMenuHitArea,
    onContextMenu: ((Offset) -> Unit)?,
    onBadgeEnter: (() -> Unit)?,
    hasFileMenu: Boolean,
    references: AuroraReferenceInteraction?,
    referenceRow: AuroraReferenceRow?,
): Modifier = pointerInput(hitArea, onContextMenu, onBadgeEnter, hasFileMenu, references, referenceRow) {
    awaitPointerEventScope {
        var referenceStart: Pair<AuroraReferenceRow.Region, Int?>? = null
        var accumulate = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
            when (event.type) {
                PointerEventType.Exit -> hitArea.leave(mouse.position)
                PointerEventType.Enter, PointerEventType.Move -> {
                    val enteredBadge = hitArea.move(mouse.position)
                    if (enteredBadge && hitArea.opensOnBadgeEnter && !mouse.pressed) onBadgeEnter?.invoke()
                }
                PointerEventType.Scroll -> hitArea.move(mouse.position)
                PointerEventType.Press -> {
                    val root = hitArea.rootOrigin + mouse.position
                    val region = referenceRow?.regionAt(root)
                    if (event.buttons.isPrimaryPressed && region != null && references != null) {
                        accumulate = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
                        referenceRow?.regions?.forEach { it.range = null }
                        referenceStart = region to region.offsetAt(root, end = false)
                        if (accumulate && region.objectSelectable) {
                            references.toggleObject(region.source, region.rawText)
                            mouse.consume()
                            referenceStart = null
                        }
                    }
                    if (onContextMenu != null && event.buttons.isSecondaryPressed && hitArea.containsMessage(mouse.position)) {
                        hitArea.attachmentMenuHandled = false
                        if (hasFileMenu && (referenceRow == null || region?.source?.kind == AuroraReferenceSelection.Kind.Attachment)
                            && references?.hasSelection(referenceRow?.selected(region)) != true
                        ) {
                            // The attachment retains its file actions when there is no active selection.
                            awaitPointerEvent(PointerEventPass.Final)
                        }
                        if (!hitArea.attachmentMenuHandled) {
                            mouse.consume()
                            onContextMenu(root)
                        }
                    }
                }
                PointerEventType.Release -> {
                    val start = referenceStart
                    referenceStart = null
                    if (start != null && referenceRow != null && references != null) {
                        val (region, first) = start
                        val last = region.offsetAt(hitArea.rootOrigin + mouse.position, end = true)
                        if (first != null && last != null && first != last) {
                            region.range = AuroraReferenceSelection.Range(minOf(first, last), maxOf(first, last))
                        }
                        // The Foundation SelectionContainer commits the last selected texts in the main pass.
                        awaitPointerEvent(PointerEventPass.Final)
                        val selected = referenceRow.selected(region)
                        if (selected != null) {
                            if (accumulate) references.rememberSelection(selected, region.selection)
                            else references.trackSelection(region.selection ?: referenceRow.selection)
                        } else if (accumulate && region.range != null) {
                            references.rememberSelection(region.source.copy(range = region.range), region.selection)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun Modifier.openMenuOnHold(open: () -> Unit): Modifier =
    semantics { onLongClick(MessageActionsLabel) { open(); true } }.pointerInput(open) {
        awaitEachGesture {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() } ?: return@awaitEachGesture
            val action = awaitMenuLongPressRelease(down.id)
            if (action != null) {
                action.consume()
                open()
            }
        }
    }

private suspend fun AwaitPointerEventScope.awaitMenuLongPressRelease(id: PointerId): PointerInputChange? {
    val held = awaitLongPressOrCancellation(id) ?: return null
    var pointer: PointerInputChange?
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        pointer = event.changes.firstOrNull { it.id == id }
            ?.takeIf { (it.position - held.position).getDistance() <= viewConfiguration.touchSlop }
    } while (pointer?.pressed == true)
    return pointer
}

@Suppress("DEPRECATION")
@Composable
private fun auroraMessageActions(
    message: Message, touch: Boolean, onAction: (AiStudioAction) -> Unit, liveText: StateFlow<LiveText>?,
): List<HbMenuAction> {
    val clipboard = LocalClipboardManager.current
    val hasStream = liveText != null && remember(liveText, message.id) {
        liveText.map { it.textFor(message.id) != null }.distinctUntilChanged()
    }.collectAsState(liveText.value.textFor(message.id) != null).value
    return remember(message, touch, onAction, clipboard, hasStream, liveText) {
        buildList {
            val text = when (message) {
                is User -> message.text
                is Reply -> message.text.ifBlank { message.interim.orEmpty() }
            }
            if (text.isNotEmpty() || hasStream) {
                add(HbMenuAction("Копировать", {
                    val current = liveText?.value?.textFor(message.id)
                    clipboard.setText(AnnotatedString(current ?: text))
                }, glyph = HbIconGlyph.Copy))
            }
            if (message is User && !message.answers) {
                add(HbMenuAction("Редактировать", { onAction(AiStudioAction.EditMessage(message.id)) },
                    glyph = HbIconGlyph.Pencil))
            }
            val retryAllowed = when (message) {
                is User -> message.retryAllowed
                is Reply -> message.retryAllowed
            }
            if (message.failed && retryAllowed) {
                val label = if (!touch && message is User) "Отправить повторно" else "Повторить отправку"
                add(HbMenuAction(label, { onAction(AiStudioAction.Retry(message.id, (message as? User)?.revisionId)) },
                    glyph = HbIconGlyph.Retry, destructive = true))
            }
            if (touch && message is Reply && message.result != null) {
                add(HbMenuAction("Открыть результат", { onAction(AiStudioAction.OpenResult(message.id)) },
                    glyph = HbIconGlyph.FolderOpen))
            }
        }
    }
}

private val Message.failed: Boolean
    get() = when (this) {
        is User -> delivery == Delivery.Failed
        is Reply -> status == ReplyStatus.Failed
    }

private const val MessageActionsLabel = "Действия сообщения"
