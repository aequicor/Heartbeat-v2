package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

internal val AuroraReadingScrollbarEndInset = maxOf(AuroraLayout.TimerThickness, AuroraLayout.Space / 2)
internal val AuroraReadingScrollbarGutter = AuroraLayout.NavigationTrack + AuroraReadingScrollbarEndInset

@Suppress("CompositionLocalAllowlist", "NoNonPrivateGlobalVariables")
internal val LocalAuroraReadingResizePointerHeldChange = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }

private const val ResizeMarkOpacity = 0.45f

@Composable
internal fun AuroraResizeHandle(
    readingWidth: Dp,
    direction: Int,
    onWidthChange: (Dp) -> Unit,
    onReset: () -> Unit,
    hitAreaPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val focusRing = if (focused && HbFocusIndication.keyboardNavigation) {
        Modifier.border(AuroraLayout.Stroke, HbTheme.colors.focus, HbShapes.Small)
    } else Modifier
    val currentWidth by rememberUpdatedState(readingWidth)
    val resize by rememberUpdatedState(onWidthChange)
    Box(modifier.width(AuroraLayout.Space).fillMaxHeight().then(ShareHistoryPointerInput)) {
        Box(
            Modifier.padding(hitAreaPadding).fillMaxSize()
                .auroraResizeHandleCursor(LocalAuroraReadingResizePointerHeldChange.current)
                .semantics { contentDescription = "Изменить ширину диалога" }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = AuroraLayout.ReadingWidthStep * direction
                    when (event.key) {
                        Key.DirectionLeft -> onWidthChange(readingWidth - step)
                        Key.DirectionRight -> onWidthChange(readingWidth + step)
                        Key.MoveHome -> onReset()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .pointerInput(direction) {
                    var startWidth = Dp.Hairline
                    var displacement = 0f
                    try {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                startWidth = currentWidth
                                displacement = 0f
                                dragging = true
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, amount ->
                            change.consume()
                            displacement += amount
                            // The column is centered, so a single edge moves by half the width change.
                            resize(startWidth + (displacement * 2 * direction).toDp())
                        }
                    } finally {
                        dragging = false
                    }
                }
                .combinedClickable(
                    interactionSource = interaction, indication = null, onDoubleClick = onReset, onClick = {},
                )
                .then(focusRing),
        )
        if (hovered || dragging) {
            Box(
                Modifier.align(Alignment.Center)
                    .size(AuroraLayout.ResizeMarkWidth, AuroraLayout.ResizeMarkHeight)
                    .background(AuroraColors.Primary.copy(alpha = ResizeMarkOpacity)),
            )
        }
    }
}

/** Keeps the history's wheel input available over a resize handle. */
private data object ShareHistoryPointerInput : ModifierNodeElement<ShareHistoryPointerInput.Node>() {

    override fun create() = Node()

    override fun update(node: Node) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "shareHistoryPointerInput"
    }

    class Node : Modifier.Node(), PointerInputModifierNode {

        override fun sharePointerInputWithSiblings() = true

        override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit

        override fun onCancelPointerInput() = Unit
    }
}
