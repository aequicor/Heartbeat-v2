@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Radio group: exactly one selected option from a small set of peers (HB-DS-018). The group is a
 * `selectableGroup`, so assistive services announce the options as one choice. The visible option
 * label is the accessible name of each option.
 */
@Composable
fun <T> HbRadioGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val focusRequesters = remember(options.size) { List(options.size) { FocusRequester() } }
    var focusedIndex by remember(options.size) { mutableStateOf(-1) }

    Column(
        modifier = modifier
            .selectableGroup()
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (!enabled || options.isEmpty() || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                // The arrow moves from the actually focused option, not from the selected one: a
                // focus that moved programmatically must not make the next arrow jump back.
                val anchor = focusedIndex.takeIf { it in options.indices } ?: options.indexOf(selected)
                val target = when (event.key) {
                    Key.DirectionDown -> (anchor + 1).coerceIn(0, options.lastIndex)
                    Key.DirectionUp -> (anchor - 1).coerceIn(0, options.lastIndex)
                    else -> return@onPreviewKeyEvent false
                }
                focusRequesters[target].requestFocus()
                onSelect(options[target])
                true
            },
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        options.forEachIndexed { index, option ->
            HbRadioOption(
                label = optionLabel(option),
                selected = option == selected,
                enabled = enabled,
                onClick = { onSelect(option) },
                onFocus = { focusedIndex = index },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
private fun HbRadioOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val labelColor = if (enabled) colors.text.primary else colors.text.primary.copy(HbEffects.DisabledContent)
    val indicatorColor = when {
        !enabled -> colors.text.disabled
        else -> colors.accent.brand
    }

    Row(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .clip(HbShapes.Small)
            .heightIn(min = HbSizes.minimumTarget)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Canvas(modifier = Modifier.size(radioSize)) {
            drawCircle(
                color = indicatorColor,
                radius = size.minDimension / 2f,
                style = Stroke(width = size.minDimension * RadioRingRatio),
            )
            if (selected) {
                drawCircle(
                    color = indicatorColor,
                    radius = size.minDimension * RadioDotRatio,
                )
            }
        }
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { labelColor },
        )
    }
}

private val radioSize = 18.dp

private const val RadioRingRatio = 0.12f

private const val RadioDotRatio = 0.28f
