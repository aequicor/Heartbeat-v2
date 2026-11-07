@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
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
 */
@Composable
fun HbTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = HbTheme.colors
    val triggerInteraction = remember { MutableInteractionSource() }
    val tooltipInteraction = remember { MutableInteractionSource() }
    val hovered by triggerInteraction.collectIsHoveredAsState()
    val tooltipHovered by tooltipInteraction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val active = hovered || focused || tooltipHovered

    LaunchedEffect(active) {
        if (active) {
            delay(HbMotions.Default.tooltipHoverDelayMs.toLong())
            visible = true
        } else {
            visible = false
        }
    }

    Box(
        modifier = modifier
            .hoverable(triggerInteraction)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && visible) {
                    visible = false
                    true
                } else {
                    false
                }
            },
    ) {
        content()
        if (visible) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -TooltipOffset),
                onDismissRequest = { visible = false },
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = TOOLTIP_MAX_WIDTH)
                        .hoverable(tooltipInteraction)
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
}

private val TOOLTIP_MAX_WIDTH = 280.dp

private const val TooltipOffset = 32

private val TOOLTIP_BORDER = 1.dp
