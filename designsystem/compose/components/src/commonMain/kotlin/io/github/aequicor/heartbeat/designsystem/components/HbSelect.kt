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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * does not query a model catalog or decide availability. The trigger announces its label, the
 * selected value and the expanded state through semantics, keeps the shared focus outline, closes
 * the popup when it becomes disabled, and reads the label and error of the surrounding
 * [HbFormField].
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
    val popupOffset = with(LocalDensity.current) { POPUP_OFFSET.roundToPx() }
    val formLabel = HbFormFieldLocals.LocalLabel.current
    val formError = HbFormFieldLocals.LocalError.current
    val effectiveLabel = label.ifEmpty { formLabel.orEmpty() }

    if (!enabled) expanded = false

    Box(modifier = modifier) {        SelectTrigger(
            text = selected?.let(optionLabel) ?: placeholder,
            isPlaceholder = selected == null,
            expanded = expanded,
            enabled = enabled,
            label = effectiveLabel,
            value = selected?.let(optionLabel),
            error = formError,
            onClick = {
                if (enabled && !expanded) {
                    activeIndex = options.indexOf(selected).coerceAtLeast(0)
                }
                if (enabled) expanded = !expanded
            },
            onExpandedChange = { expanded = it },
        )

        if (expanded && enabled) {
            Popup(
                popupPositionProvider = remember(popupOffset) { HbAnchoredPopupPositionProvider(gap = popupOffset) },
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
    value: String?,
    error: String?,
    onClick: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val border = when {
        error != null -> colors.status.error.foreground
        else -> colors.border.control
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.controlHeight)
            .clip(HbShapes.Medium)
            .background(colors.surface.input)
            .border(FIELD_BORDER, border, HbShapes.Medium)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = enabled && focused, shape = HbShapes.Medium, color = colors.focus)
            .clickable(
                enabled = enabled,
                role = Role.DropdownList,
                onClick = onClick,
            )
            .selectSemantics(
                enabled = enabled,
                label = label,
                value = value,
                expanded = expanded,
                error = error,
                onExpandedChange = onExpandedChange,
            )
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

private fun Modifier.selectSemantics(
    enabled: Boolean,
    label: String,
    value: String?,
    expanded: Boolean,
    error: String?,
    onExpandedChange: (Boolean) -> Unit,
): Modifier = semantics {
    if (label.isNotEmpty()) contentDescription = label
    if (value != null) stateDescription = value
    if (enabled) {
        if (expanded) {
            collapse { onExpandedChange(false); true }
        } else {
            expand { onExpandedChange(true); true }
        }
    }
    if (error != null) this.error(error)
}


private val FIELD_BORDER = 1.dp

private val POPUP_OFFSET = 4.dp
