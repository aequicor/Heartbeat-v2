package io.github.aequicor.heartbeat.designsystem.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Checkbox with an independent or mixed selection (HB-DS-018). The visible [label] is also the
 * accessible name, so the state is announced with its meaning.
 *
 * States: unchecked, checked and [indeterminate]; disabled dims the mark and the label; keyboard
 * focus draws the focus outline around the row, which stays visible over selection. The mark is
 * drawn with the shared icon vocabulary.
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
    val colors = HbThemeTokens.colors
    var focused by remember { mutableStateOf(false) }
    val state = when {
        indeterminate -> ToggleableState.Indeterminate
        checked -> ToggleableState.On
        else -> ToggleableState.Off
    }
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
        CheckboxMark(
            state = state,
            enabled = enabled,
            borderColor = colors.border.control,
        )
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { labelColor },
        )
    }
}

@Composable
private fun CheckboxMark(
    state: ToggleableState,
    enabled: Boolean,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = HbThemeTokens.colors
    val marked = state != ToggleableState.Off
    val background = when {
        !enabled -> colors.text.disabled
        marked -> colors.accent.brand
        else -> colors.surface.input
    }
    val foreground = if (enabled) colors.surface.base else colors.surface.input

    Box(
        modifier = modifier
            .size(checkboxSize)
            .clip(HbShapes.Small)
            .background(background)
            .then(
                if (marked) {
                    Modifier
                } else {
                    Modifier.border(checkboxBorder, borderColor, HbShapes.Small)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.Indeterminate -> HbIcon(
                glyph = HbIconGlyph.Minus,
                tint = foreground,
                size = markSize,
            )

            ToggleableState.On -> HbIcon(
                glyph = HbIconGlyph.Check,
                tint = foreground,
                size = markSize,
            )

            ToggleableState.Off -> Unit
        }
    }
}

private val checkboxSize = 18.dp

private val markSize = 12.dp

private val checkboxBorder = 1.dp
