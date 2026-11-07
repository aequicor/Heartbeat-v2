@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Checkbox with an independent or mixed selection (HB-DS-018). The visible [label] is also the
 * accessible name, so the state is announced with its meaning.
 *
 * States: unchecked, checked and [indeterminate]; disabled dims the mark and the label; keyboard
 * focus draws the focus outline around the row, which stays visible over selection.
 */
@Composable
fun HbCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    indeterminate: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = HbThemeDefaults.LocalColors.current
    var focused by remember { mutableStateOf(false) }
    val state = when {
        indeterminate -> ToggleableState.Indeterminate
        checked -> ToggleableState.On
        else -> ToggleableState.Off
    }
    val marked = state != ToggleableState.Off
    val markBackground = when {
        !enabled -> colors.text.disabled
        marked -> colors.accent.brand
        else -> colors.surface.input
    }
    val markForeground = if (enabled) colors.surface.base else colors.surface.input
    val labelColor = if (enabled) colors.text.primary else colors.text.primary.copy(HbEffects.DisabledContent)

    Row(
        modifier = modifier
            .clip(HbShapes.Small)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .triStateToggleable(
                state = state,
                onClick = { onCheckedChange(!checked) },
                enabled = enabled,
                role = Role.Checkbox,
            )
            .padding(vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Box(
            modifier = Modifier
                .size(checkboxSize)
                .clip(HbShapes.Small)
                .background(markBackground)
                .then(
                    if (marked) {
                        Modifier
                    } else {
                        Modifier.border(checkboxBorder, colors.border.control, HbShapes.Small)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state == ToggleableState.Indeterminate -> IndeterminateMark(color = markForeground)
                state == ToggleableState.On -> CheckMark(color = markForeground)
            }
        }
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { labelColor },
        )
    }
}

@Composable
private fun CheckMark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(markSize)) {
        val stroke = size.minDimension * MarkStrokeRatio
        val start = Offset(size.width * MarkStartX, size.height * MarkStartY)
        val middle = Offset(size.width * MarkMiddleX, size.height * MarkMiddleY)
        val end = Offset(size.width * MarkEndX, size.height * MarkEndY)

        drawLine(color, start, middle, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, middle, end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun IndeterminateMark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(markSize)) {
        drawLine(
            color = color,
            start = Offset(size.width * MarkStartX, size.height / 2f),
            end = Offset(size.width * MarkEndX, size.height / 2f),
            strokeWidth = size.minDimension * MarkStrokeRatio,
            cap = StrokeCap.Round,
        )
    }
}

private val checkboxSize = 18.dp

private val markSize = 12.dp

private val checkboxBorder = 1.dp

private const val MarkStrokeRatio = 0.16f

private const val MarkStartX = 0.18f

private const val MarkStartY = 0.52f

private const val MarkMiddleX = 0.4f

private const val MarkMiddleY = 0.74f

private const val MarkEndX = 0.82f

private const val MarkEndY = 0.26f
