package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Segmented control for a small set of peer choices (HB-DS-018). Each segment is a radio option in
 * one `selectableGroup`, so the choice is announced as a group and the selection is not encoded by
 * color alone: the selected segment has a filled container.
 *
 * Long localized labels grow the row height instead of being squeezed into fixed equal widths.
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

    Row(
        modifier = modifier
            .clip(HbShapes.Medium)
            .border(segmentBorder, colors.border.control, HbShapes.Medium)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            Segment(
                label = optionLabel(option),
                selected = option == selected,
                enabled = enabled,
                onClick = { onSelect(option) },
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
