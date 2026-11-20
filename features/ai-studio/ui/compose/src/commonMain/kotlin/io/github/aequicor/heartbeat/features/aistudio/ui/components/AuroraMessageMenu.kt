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
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureScope
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
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuMotion
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import kotlin.math.roundToInt

@Composable
internal fun AuroraMessageMenu(
    message: Message,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    readingWidth: Dp,
    selection: SelectionState,
    modifier: Modifier = Modifier,
    liveText: StateFlow<LiveText>? = null,
    content: @Composable (hoverOverMenu: Boolean) -> Unit,
) {
    val actions = auroraMessageActions(message, onAction, liveText)
    val user = message is User
    val hitArea = remember { MessageMenuHitArea() }
    val menu = remember { MessageMenuState() }
    val expanded = menu.expanded
    val onContextMenu = menu.openAtPointer.takeIf { !touch && actions.isNotEmpty() }
    val touchAtEdge = touch && !hitArea.outsideFits
    SideEffect(expanded) {
        if (expanded && touchAtEdge) selection.clear()
    }
    CloseMessageMenuOnHoverExit(
        expanded && menu.openedByHover, hitArea.popupHovered, hitArea.overBadge, hitArea.overMessage, menu.close,
    )

    AuroraMessageMenuLayout(
        user, touch, readingWidth, hitArea,
        bubble = {
            val atEdge = if (touchAtEdge && actions.isNotEmpty()) Modifier.openMenuOnHold(menu.open)
            else Modifier
            Box(atEdge) { content(hitArea.hoveredOutsideBubble && actions.isNotEmpty()) }
        },
        menu = {
            if (actions.isNotEmpty()) {
                HbMenu(
                    expanded, menu.close, actions,
                    presentation = if (touch) {
                        HbMenuPresentation.AuroraTouchMessage
                    } else HbMenuPresentation.AuroraDesktopMessageActions,
                    alignEnd = user,
                    restoreTriggerFocus = !touchAtEdge,
                    pointerAnchor = menu.pointerAnchor,
                    motion = HbMenuMotion(
                        replaceTrigger = menu.pointerAnchor == null,
                        hoverMenu = menu.openedByHover,
                        onHover = hitArea.trackPopupHover,
                    ).takeUnless { touch },
                ) { trigger ->
                    AuroraMessageMenuTrigger(hitArea, expanded, touch, menu.open, menu.openOnHover, menu.close, trigger)
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
        modifier = modifier.trackMessageMenuPointer(hitArea, onContextMenu),
    )
}

private class MessageMenuState {
    var expanded by mutableStateOf(false)
        private set
    var pointerAnchor by mutableStateOf<IntOffset?>(null)
        private set
    var openedByHover by mutableStateOf(false)
        private set

    val open: () -> Unit = { show(null, byHover = false) }
    val openOnHover: () -> Unit = { show(null, byHover = true) }
    val openAtPointer: (IntOffset) -> Unit = { show(it, byHover = false) }
    val close: () -> Unit = { expanded = false }

    private fun show(anchor: IntOffset?, byHover: Boolean) {
        pointerAnchor = anchor
        openedByHover = byHover
        expanded = true
    }
}

@Composable
private fun AuroraMessageMenuLayout(
    user: Boolean,
    touch: Boolean,
    readingWidth: Dp,
    hitArea: MessageMenuHitArea,
    bubble: @Composable () -> Unit,
    menu: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(bubble, menu, footer), modifier = modifier.fillMaxWidth()) { slots, constraints ->
        val (bodySlot, menuSlot, footerSlot) = slots
        val width = constraints.maxWidth
        val reading = if (readingWidth.value.isFinite()) minOf(width, readingWidth.roundToPx()) else width
        val gap = AuroraLayout.SmallGap.roundToPx()
        val wideEdge = AuroraLayout.FrameRadius.roundToPx() + gap
        val edge = if (reading >= AuroraLayout.Transcript.roundToPx() + wideEdge * 2) wideEdge
        else AuroraLayout.PaneGap.roundToPx()
        val column = (reading - edge * 2).coerceIn(0, AuroraLayout.Transcript.roundToPx())
        val left = (width - column) / 2
        val right = left + column
        val body = bodySlot.single().measure(constraints.copy(minWidth = 0, maxWidth = column))
        val stamp = if (body.needsExternalTimestamp()) {
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
            AuroraLayout.MessageMenuBadge.toPx() / 2,
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
    onOpen: () -> Unit,
    onHoverOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = remember { MutableInteractionSource() }
    val focused by keyboard.collectIsFocusedAsState()
    val focusVisible = focused && HbFocusIndication.keyboardNavigation
    val outside = hitArea.outsideFits
    val desktopHover = outside && !touch
    val badgeHovered = desktopHover && hitArea.overBadge
    val reveal = rememberMessageActionBadge(
        hovered = desktopHover && (hitArea.hovered || hitArea.overBadge),
        overBubble = desktopHover && hitArea.overMessage,
        pinned = focusVisible || outside && touch,
        expanded = expanded,
        touch = touch,
    )
    OpenMessageMenuOnBadgeHover(badgeHovered, expanded, reveal.interactive, onHoverOpen)
    val pointer = if (reveal.interactive) {
        Modifier.pointerInput(expanded, onOpen, onClose) {
            val click = if (expanded) onClose else onOpen
            detectTapGestures(onTap = { click() })
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
        HbIcon(HbIconGlyph.ChevronDown, tint = AuroraColors.MessageInk, size = AuroraLayout.Glyph)
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
    private var badgeRadius = 0f
    var outsideFits by mutableStateOf(false)
        private set
    var hovered by mutableStateOf(false)
        private set
    var overMessage by mutableStateOf(false)
        private set
    var overBadge by mutableStateOf(false)
        private set
    var popupHovered by mutableStateOf(false)
        private set
    val hoveredOutsideBubble get() = hovered && !overMessage || popupHovered

    val trackPopupHover: (Boolean) -> Unit = { hovered ->
        popupHovered = hovered
        if (hovered) {
            overMessage = false
            overBadge = false
            this.hovered = false
        }
    }

    fun place(message: Rect, trigger: Rect, outside: Boolean, radius: Float) {
        messageBounds = message
        triggerBounds = trigger
        badgeRadius = radius
        connectedBounds = Rect(
            left = minOf(message.left, trigger.left),
            top = trigger.top,
            right = maxOf(message.right, trigger.right),
            bottom = trigger.bottom,
        )
        outsideFits = outside
    }

    fun move(point: Offset) {
        overMessage = messageBounds.contains(point)
        hovered = overMessage || outsideFits && connectedBounds.contains(point)
        overBadge = isOnBadge(point)
    }

    fun leave() {
        overMessage = false
        hovered = false
        overBadge = false
    }

    fun contextAnchor(point: Offset): IntOffset? {
        if (!messageBounds.contains(point)) return null
        val relative = point - triggerBounds.topLeft
        return IntOffset(relative.x.roundToInt(), relative.y.roundToInt())
    }

    private fun isOnBadge(point: Offset) = outsideFits && (point - triggerBounds.center).getDistance() <= badgeRadius
}

private fun messageMenuBounds(
    parentWidth: Int, x: Int, y: Int, width: Int, height: Int, mirror: Boolean,
): Rect {
    val left = (if (mirror) parentWidth - x - width else x).toFloat()
    return Rect(left, y.toFloat(), left + width, (y + height).toFloat())
}

private fun Modifier.trackMessageMenuPointer(
    hitArea: MessageMenuHitArea,
    onContextMenu: ((IntOffset) -> Unit)?,
): Modifier = pointerInput(hitArea, onContextMenu) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
            when (event.type) {
                PointerEventType.Exit -> hitArea.leave()
                PointerEventType.Enter, PointerEventType.Move, PointerEventType.Scroll -> hitArea.move(mouse.position)
                PointerEventType.Press -> if (onContextMenu != null && event.buttons.isSecondaryPressed) {
                    hitArea.contextAnchor(mouse.position)?.let { anchor ->
                        mouse.consume()
                        onContextMenu(anchor)
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
    message: Message, onAction: (AiStudioAction) -> Unit, liveText: StateFlow<LiveText>?,
): List<HbMenuAction> {
    val clipboard = LocalClipboardManager.current
    val hasStream = liveText != null && remember(liveText, message.id) {
        liveText.map { it.textFor(message.id) != null }.distinctUntilChanged()
    }.collectAsState(liveText.value.textFor(message.id) != null).value
    return remember(message, onAction, clipboard, hasStream, liveText) {
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
            if (message.failed) {
                add(HbMenuAction("Повторить отправку", { onAction(AiStudioAction.Retry(message.id)) },
                    glyph = HbIconGlyph.Retry, destructive = true))
            }
            if (message is Reply && message.result != null) {
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
