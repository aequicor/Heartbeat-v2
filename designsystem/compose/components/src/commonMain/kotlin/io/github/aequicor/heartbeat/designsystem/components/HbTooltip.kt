@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import kotlinx.coroutines.delay

/**
 * Supplementary label or shortcut hint (HB-DS-029).
 *
 * The hint appears after the hover delay, stays while the trigger or the tooltip itself is hovered
 * or while the trigger keeps the focus, and Escape dismisses it without moving the focus. It never
 * times out while being read and never contains an action that exists nowhere else: a tooltip is
 * not a place for required operations.
 *
 * The pointer can leave the trigger before it reaches the hint, so the surface closes after a short
 * grace period instead of on the first missing hover; that keeps the documented "moving the pointer
 * onto the tooltip does not close it" behavior. The popup is placed by the collision-aware provider:
 * it stays inside a short or narrow window instead of being clipped by the screen edge.
 */
@Composable
fun HbTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val triggerInteraction = remember { MutableInteractionSource() }
    val tooltipInteraction = remember { MutableInteractionSource() }
    val hovered by triggerInteraction.collectIsHoveredAsState()
    val tooltipHovered by tooltipInteraction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    val pointerOnPair = hovered || tooltipHovered
    val active = (pointerOnPair || focused) && !dismissed

    LaunchedEffect(active) {
        if (active) {
            if (!visible) delay(HbMotions.Default.tooltipHoverDelayMs.toLong())
            visible = true
        } else if (visible) {
            delay(TooltipHideDelayMs)
            visible = false
        }
    }

    SideEffect {
        if (!pointerOnPair && !focused) dismissed = false
    }

    Box(
        modifier = modifier
            .hoverable(triggerInteraction)
            .onFocusChanged { focused = it.hasFocus }
            .tooltipKeys(
                visible = visible,
                onDismiss = {
                    visible = false
                    dismissed = true
                },
            )
            .semantics {
                if (active) stateDescription = text
            },
    ) {
        content()
        if (visible) {
            TooltipPopup(text = text, interactionSource = tooltipInteraction)
        }
    }
}

private fun Modifier.tooltipKeys(visible: Boolean, onDismiss: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && visible) {
            onDismiss()
            true
        } else {
            false
        }
    }

@Composable
private fun TooltipPopup(text: String, interactionSource: MutableInteractionSource) {
    val colors = HbTheme.colors
    val gap = with(LocalDensity.current) { TooltipGap.roundToPx() }

    Popup(
        popupPositionProvider = HbAnchoredPopupPositionProvider(gap = gap, preferAbove = true),
        onDismissRequest = {},
        properties = PopupProperties(focusable = false),
    ) {
        BoxWithConstraints {
            val width = minOf(TOOLTIP_MAX_WIDTH, maxWidth)
            Box(
                modifier = Modifier
                    .widthIn(max = width)
                    .hoverable(interactionSource)
                    .clip(HbShapes.Small)
                    .background(colors.surface.raised)
                    .border(TOOLTIP_BORDER, colors.border.subtle, HbShapes.Small)
                    .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
            ) {
                BasicText(
                    text = text,
                    style = HbTypography.Caption,
                    color = { colors.text.primary },
                )
            }
        }
    }
}

private val TOOLTIP_MAX_WIDTH = 280.dp

private val TooltipGap = 6.dp

private val TOOLTIP_BORDER = 1.dp

private const val TooltipHideDelayMs = 180L
