package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Segmented control for a small set of peer choices (HB-DS-018). Each segment is a radio option in
 * one `selectableGroup`, so the choice is announced as a group and the selection is not encoded by
 * color alone: the selected segment has a filled container.
 *
 * The group is one keyboard group: the left and right arrows move the selection and the focus
 * together, mirrored in a right-to-left layout. Long localized labels grow the row height instead
 * of being squeezed into fixed equal widths, every segment keeps the density minimum target, and
 * each focused segment draws the shared focus outline.
 */
@Composable
fun <T> HbSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val colors = HbTheme.colors
    val focusRequesters = remember(options.size) { List(options.size) { FocusRequester() } }
    val layoutDirection = LocalLayoutDirection.current

    Row(
        modifier = modifier
            .border(segmentBorder, colors.border.control, HbShapes.Medium)
            .selectableGroup()
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (!enabled || options.isEmpty() || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val step = when (event.key) {
                    Key.DirectionRight -> 1
                    Key.DirectionLeft -> -1
                    else -> return@onPreviewKeyEvent false
                }
                val direction = if (layoutDirection == LayoutDirection.Rtl) -step else step
                val target = (options.indexOf(selected) + direction).coerceIn(0, options.lastIndex)
                focusRequesters[target].requestFocus()
                onSelect(options[target])
                true
            },
    ) {
        options.forEachIndexed { index, option ->
            Segment(
                label = optionLabel(option),
                selected = option == selected,
                enabled = enabled,
                onClick = { onSelect(option) },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
private fun RowScope.Segment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val background = if (selected) colors.selection.background else Color.Transparent
    val contentColor = when {
        !enabled -> colors.text.primary.copy(HbEffects.DisabledContent)
        selected -> colors.selection.foreground
        else -> colors.text.primary
    }

    Box(
        modifier = modifier
            .weight(1f)
            .clip(HbShapes.Medium)
            .background(background)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = enabled && focused, shape = HbShapes.Medium, color = colors.focus)
            .heightIn(min = HbSizes.minimumTarget)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.S),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { contentColor },
        )
    }
}

private val segmentBorder = 1.dp
