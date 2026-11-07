@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Selection from a closed list (HB-DS-018).
 *
 * The trigger is a field-shaped control with the `DropdownList` role; the options open in a
 * focusable popup with keyboard navigation. The options are supplied by the owner, so the component
 * does not query a model catalog or decide availability.
 */
@Composable
fun <T> HbSelect(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    optionLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(0) }
    val popupOffset = with(LocalDensity.current) {
        HbSizes.controlHeight.roundToPx() + POPUP_OFFSET.roundToPx()
    }

    Box(modifier = modifier) {
        SelectTrigger(
            text = selected?.let(optionLabel) ?: placeholder,
            isPlaceholder = selected == null,
            expanded = expanded,
            enabled = enabled,
            label = label,
            onClick = {
                if (!expanded) {
                    activeIndex = options.indexOf(selected).coerceAtLeast(0)
                }
                expanded = !expanded
            },
        )

        if (expanded) {
            Popup(
                popupPositionProvider = remember { HbAnchoredPopupPositionProvider(gap = popupOffset) },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                HbOptionList(
                    options = options,
                    selected = selected,
                    activeIndex = activeIndex,
                    optionLabel = optionLabel,
                    onSelect = { option ->
                        onSelect(option)
                        expanded = false
                    },
                    onActiveChange = { activeIndex = it },
                    onDismiss = { expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SelectTrigger(
    text: String,
    isPlaceholder: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.controlHeight)
            .clip(HbShapes.Medium)
            .background(colors.surface.input)
            .border(FIELD_BORDER, colors.border.control, HbShapes.Medium)
            .clickable(
                enabled = enabled,
                role = Role.DropdownList,
                onClick = onClick,
            )
            .semantics {
                if (label.isNotEmpty()) contentDescription = label
            }
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = text,
            style = HbTypography.Label,
            color = { if (isPlaceholder) colors.text.secondary else colors.text.primary },
            modifier = Modifier.weight(1f),
        )
        HbChevronGlyph(color = colors.text.secondary, expanded = expanded)
    }
}


private val FIELD_BORDER = 1.dp

private val POPUP_OFFSET = 4.dp
